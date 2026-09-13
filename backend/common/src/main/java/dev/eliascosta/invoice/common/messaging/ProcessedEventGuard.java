package dev.eliascosta.invoice.common.messaging;

import dev.eliascosta.invoice.common.event.EventEnvelope;
import java.time.Clock;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Consumer-side idempotency (RNF-RES-03, RN-17, {@code requisitos.md} §6.7): Kafka delivers at
 * least once, so a listener claims each event for its consumer group <em>inside the transaction of
 * the effect</em> and skips the event when the claim fails.
 *
 * <pre>{@code
 * @KafkaListener(topics = "stock.debit-requested", groupId = "stock-service")
 * @Transactional
 * public void on(String json) {
 *   EventEnvelope<DebitRequested> event = codec.fromJson(json, DebitRequested.class);
 *   if (!guard.tryMark(event, "stock-service")) {
 *     return; // duplicate delivery
 *   }
 *   // apply the effect; the mark is committed (or rolled back) together with it
 * }
 * }</pre>
 *
 * <p>This is the atomic form of the flow in {@code modelo-de-dados.md} §4.4 ("check {@code
 * processed_events} first, insert it last"): the mark is inserted first but only becomes visible
 * with the commit of the effect, so the outcome is identical and free of races.
 */
public class ProcessedEventGuard {

  private static final Logger log = LoggerFactory.getLogger(ProcessedEventGuard.class);

  private final ProcessedEventRepository repository;
  private final Clock clock;

  public ProcessedEventGuard(ProcessedEventRepository repository, Clock clock) {
    this.repository = Objects.requireNonNull(repository, "repository");
    this.clock = Objects.requireNonNull(clock, "clock");
  }

  /**
   * Claims the event for the group.
   *
   * @return {@code true} when this is the first delivery to the group (apply the effect); {@code
   *     false} when the event was already processed (skip it)
   * @throws IllegalStateException when called outside a transaction
   */
  public boolean tryMark(UUID eventId, String eventType, String consumerGroup) {
    Objects.requireNonNull(eventId, "eventId");
    Objects.requireNonNull(eventType, "eventType");
    Objects.requireNonNull(consumerGroup, "consumerGroup");
    if (!TransactionSynchronizationManager.isActualTransactionActive()) {
      throw new IllegalStateException(
          "ProcessedEventGuard.tryMark must run inside the transaction that applies the event"
              + " (RNF-RES-03); no transaction is active");
    }
    boolean first =
        repository.insertIfAbsent(eventId, consumerGroup, eventType, clock.instant()) == 1;
    if (!first) {
      log.info(
          "Event {} ({}) already processed by consumer group {}; skipping duplicate delivery",
          eventId,
          eventType,
          consumerGroup);
    }
    return first;
  }

  /** Same as {@link #tryMark(UUID, String, String)} with the envelope's id and type. */
  public boolean tryMark(EventEnvelope<?> envelope, String consumerGroup) {
    Objects.requireNonNull(envelope, "envelope");
    return tryMark(envelope.id(), envelope.type(), consumerGroup);
  }
}
