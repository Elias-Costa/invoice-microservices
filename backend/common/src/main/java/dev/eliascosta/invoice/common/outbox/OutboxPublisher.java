package dev.eliascosta.invoice.common.outbox;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.errors.RetriableException;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * Outbox poller ({@code requisitos.md} §8.11, RNF-PERF-04): every tick, in one transaction, locks
 * the oldest pending events ({@code FOR UPDATE SKIP LOCKED}, batch of 100), sends them one by one
 * in creation order and waits for the broker's acknowledgement before marking {@code published_at}.
 *
 * <p>Failure handling (D-015): every failed attempt increments {@code attempts} and stores {@code
 * last_error}. A <em>transient</em> failure ({@link RetriableException} anywhere in the cause
 * chain, or an acknowledgement timeout) stops the batch — the event is retried on the next tick and
 * the events behind it keep their order per key — and never abandons the event. A
 * <em>definitive</em> failure (anything else: record too large, invalid topic, authorization, …)
 * abandons the event once {@code attempts} reaches {@code max-attempts}, sets {@code failed_at} and
 * lets the batch continue.
 *
 * <p>Delivery is at-least-once: an acknowledgement that arrives after the timeout leaves the row
 * pending and the record is sent again; consumers deduplicate by event id ({@code
 * processed_events}).
 */
public class OutboxPublisher {

  private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);
  private static final TypeReference<Map<String, String>> HEADERS_TYPE = new TypeReference<>() {};

  private final OutboxEventRepository repository;
  private final KafkaOperations<String, String> kafka;
  private final TransactionTemplate transaction;
  private final JsonMapper mapper;
  private final Clock clock;
  private final int batchSize;
  private final Duration sendTimeout;
  private final int maxAttempts;

  public OutboxPublisher(
      OutboxEventRepository repository,
      KafkaOperations<String, String> kafka,
      PlatformTransactionManager transactionManager,
      JsonMapper mapper,
      Clock clock,
      int batchSize,
      Duration sendTimeout,
      int maxAttempts) {
    if (batchSize < 1) {
      throw new IllegalArgumentException("batchSize must be >= 1, got " + batchSize);
    }
    if (maxAttempts < 1) {
      throw new IllegalArgumentException("maxAttempts must be >= 1, got " + maxAttempts);
    }
    this.repository = Objects.requireNonNull(repository, "repository");
    this.kafka = Objects.requireNonNull(kafka, "kafka");
    this.transaction = new TransactionTemplate(transactionManager);
    this.mapper = Objects.requireNonNull(mapper, "mapper");
    this.clock = Objects.requireNonNull(clock, "clock");
    this.batchSize = batchSize;
    this.sendTimeout = Objects.requireNonNull(sendTimeout, "sendTimeout");
    this.maxAttempts = maxAttempts;
  }

  /**
   * Publishes one batch of pending events. Safe to call from the scheduler or by hand.
   *
   * @return number of events acknowledged by the broker in this batch
   */
  public int publishPending() {
    return Objects.requireNonNull(transaction.execute(status -> publishBatch()));
  }

  private int publishBatch() {
    List<OutboxEventEntity> batch = repository.lockPendingBatch(batchSize);
    int published = 0;
    for (OutboxEventEntity event : batch) {
      Throwable failure = send(event);
      if (failure == null) {
        event.markPublished(clock.instant());
        published++;
      } else if (!handleFailure(event, failure)) {
        break; // transient: retry next tick, keep the order of the events behind it
      }
    }
    if (!batch.isEmpty()) {
      log.debug("Outbox batch: {} locked, {} published", batch.size(), published);
    }
    return published;
  }

  /** Sends the record and waits for the acknowledgement; returns the failure, if any. */
  private @Nullable Throwable send(OutboxEventEntity event) {
    ProducerRecord<String, String> record =
        new ProducerRecord<>(event.getTopic(), event.getPartitionKey(), event.getPayload());
    Map<String, String> headers = mapper.readValue(event.getHeaders(), HEADERS_TYPE);
    headers.forEach(
        (name, value) -> record.headers().add(name, value.getBytes(StandardCharsets.UTF_8)));
    try {
      kafka.send(record).get(sendTimeout.toMillis(), TimeUnit.MILLISECONDS);
      return null;
    } catch (ExecutionException e) {
      return e.getCause() != null ? e.getCause() : e;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return e;
    } catch (TimeoutException | RuntimeException e) {
      return e;
    }
  }

  /**
   * Records the failure; abandons the event when it is definitive and the limit is reached.
   *
   * @return {@code true} when the batch may continue (event abandoned), {@code false} when it must
   *     stop (event will be retried)
   */
  private boolean handleFailure(OutboxEventEntity event, Throwable failure) {
    int attempts = event.recordFailure(describe(failure));
    boolean definitive = !isTransient(failure);
    if (definitive && attempts >= maxAttempts) {
      event.abandon(clock.instant());
      log.error(
          "Outbox event {} ({}) abandoned after {} definitive failure(s): {}",
          event.getId(),
          event.getEventType(),
          attempts,
          event.getLastError());
      return true;
    }
    log.warn(
        "Outbox event {} ({}) publication attempt {} failed{}: {}",
        event.getId(),
        event.getEventType(),
        attempts,
        definitive ? "" : " (transient)",
        event.getLastError());
    return false;
  }

  /**
   * Transient = the broker may accept the same record later: Kafka's own {@link RetriableException}
   * family (not leader, network, request timeout, …), an acknowledgement that did not arrive in
   * time, or an interrupted wait.
   */
  static boolean isTransient(Throwable failure) {
    for (Throwable t = failure; t != null; t = t.getCause()) {
      if (t instanceof RetriableException
          || t instanceof TimeoutException
          || t instanceof InterruptedException) {
        return true;
      }
    }
    return false;
  }

  private static String describe(Throwable failure) {
    Throwable root = NestedExceptionUtils.getMostSpecificCause(failure);
    return root.getClass().getName() + ": " + root.getMessage();
  }
}
