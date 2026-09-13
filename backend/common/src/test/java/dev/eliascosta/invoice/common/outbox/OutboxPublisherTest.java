package dev.eliascosta.invoice.common.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.errors.NotLeaderOrFollowerException;
import org.apache.kafka.common.errors.RecordTooLargeException;
import org.apache.kafka.common.errors.TimeoutException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.KafkaException;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.core.KafkaProducerException;
import org.springframework.kafka.support.SendResult;
import org.springframework.transaction.PlatformTransactionManager;
import tools.jackson.databind.json.JsonMapper;

/**
 * Batch semantics of the poller with the broker mocked (requisitos.md §8.11, D-015): what is sent,
 * what is marked, and how transient and definitive failures differ.
 */
class OutboxPublisherTest {

  private static final Instant NOW = Instant.parse("2026-09-13T10:00:00Z");
  private static final Instant CREATED = NOW.minusSeconds(1);
  private static final int BATCH = 100;
  private static final int MAX_ATTEMPTS = 3;

  private final OutboxEventRepository repository = mock(OutboxEventRepository.class);

  @SuppressWarnings("unchecked")
  private final KafkaOperations<String, String> kafka = mock(KafkaOperations.class);

  private final PlatformTransactionManager transactionManager =
      mock(PlatformTransactionManager.class);
  private final OutboxPublisher publisher =
      new OutboxPublisher(
          repository,
          kafka,
          transactionManager,
          JsonMapper.builder().build(),
          Clock.fixed(NOW, ZoneOffset.UTC),
          BATCH,
          Duration.ofMillis(200),
          MAX_ATTEMPTS);

  private static OutboxEventEntity pending(String topic, String key) {
    UUID id = UUID.randomUUID();
    return new OutboxEventEntity(
        id,
        "Order",
        UUID.randomUUID(),
        "sample.order-placed",
        topic,
        key,
        "{\"id\":\"" + id + "\",\"type\":\"sample.order-placed\"}",
        "{\"event-type\":\"sample.order-placed\",\"event-id\":\"" + id + "\"}",
        CREATED);
  }

  private static OutboxEventEntity pendingAfterFailures(int failures) {
    OutboxEventEntity event = pending("orders", "k");
    for (int i = 0; i < failures; i++) {
      event.recordFailure("earlier failure");
    }
    return event;
  }

  private static CompletableFuture<SendResult<String, String>> acked() {
    return CompletableFuture.completedFuture(null);
  }

  private static CompletableFuture<SendResult<String, String>> failed(Throwable cause) {
    return CompletableFuture.failedFuture(
        new KafkaProducerException(new ProducerRecord<>("orders", "k", "v"), "Failed", cause));
  }

  private static String header(ProducerRecord<String, String> record, String name) {
    return new String(record.headers().lastHeader(name).value(), StandardCharsets.UTF_8);
  }

  @SuppressWarnings("unchecked")
  private List<ProducerRecord<String, String>> sentRecords(int expected) {
    ArgumentCaptor<ProducerRecord<String, String>> records =
        ArgumentCaptor.forClass(ProducerRecord.class);
    verify(kafka, times(expected)).send(records.capture());
    return records.getAllValues();
  }

  @Test
  void sendsTheBatchInOrderWithKeyPayloadAndHeadersThenMarksPublished() {
    OutboxEventEntity first = pending("orders", "invoice-1");
    OutboxEventEntity second = pending("orders", "invoice-1");
    when(repository.lockPendingBatch(BATCH)).thenReturn(List.of(first, second));
    when(kafka.send(any(ProducerRecord.class))).thenReturn(acked());

    assertThat(publisher.publishPending()).isEqualTo(2);

    List<ProducerRecord<String, String>> sent = sentRecords(2);
    assertThat(sent.get(0).topic()).isEqualTo("orders");
    assertThat(sent.get(0).key()).isEqualTo("invoice-1");
    assertThat(sent.get(0).value()).isEqualTo(first.getPayload());
    assertThat(header(sent.get(0), OutboxWriter.EVENT_TYPE_HEADER))
        .isEqualTo("sample.order-placed");
    assertThat(header(sent.get(0), OutboxWriter.EVENT_ID_HEADER))
        .isEqualTo(first.getId().toString());
    assertThat(sent.get(1).value()).isEqualTo(second.getPayload());
    for (OutboxEventEntity event : List.of(first, second)) {
      assertThat(event.getPublishedAt()).isEqualTo(NOW);
      assertThat(event.getFailedAt()).isNull();
      assertThat(event.getAttempts()).isEqualTo(1);
      assertThat(event.getLastError()).isNull();
      assertThat(event.isPending()).isFalse();
    }
  }

  @Test
  void emptyBatchSendsNothing() {
    when(repository.lockPendingBatch(BATCH)).thenReturn(List.of());

    assertThat(publisher.publishPending()).isZero();

    verify(kafka, never()).send(any(ProducerRecord.class));
  }

  @Test
  void transientFailureStopsTheBatchAndLeavesTheEventPending() {
    OutboxEventEntity first = pending("orders", "k");
    OutboxEventEntity second = pending("orders", "k");
    when(repository.lockPendingBatch(BATCH)).thenReturn(List.of(first, second));
    when(kafka.send(any(ProducerRecord.class)))
        .thenReturn(failed(new TimeoutException("Expiring 1 record(s) for orders-0")));

    assertThat(publisher.publishPending()).isZero();

    sentRecords(1); // the second event is not attempted: order per key is preserved
    assertThat(first.isPending()).isTrue();
    assertThat(first.getAttempts()).isEqualTo(1);
    assertThat(first.getLastError())
        .startsWith(TimeoutException.class.getName())
        .contains("Expiring 1 record(s)");
    assertThat(second.getAttempts()).isZero();
    assertThat(second.isPending()).isTrue();
  }

  @Test
  void acknowledgementTimeoutIsTransient() {
    OutboxEventEntity event = pendingAfterFailures(MAX_ATTEMPTS + 2);
    when(repository.lockPendingBatch(BATCH)).thenReturn(List.of(event));
    when(kafka.send(any(ProducerRecord.class)))
        .thenReturn(new CompletableFuture<>()); // never acked

    assertThat(publisher.publishPending()).isZero();

    assertThat(event.isPending()).isTrue();
    assertThat(event.getAttempts()).isEqualTo(MAX_ATTEMPTS + 3);
    assertThat(event.getLastError())
        .startsWith(java.util.concurrent.TimeoutException.class.getName());
  }

  @Test
  void transientFailureNeverAbandonsHoweverManyAttempts() {
    OutboxEventEntity event = pendingAfterFailures(50);
    when(repository.lockPendingBatch(BATCH)).thenReturn(List.of(event));
    when(kafka.send(any(ProducerRecord.class)))
        .thenReturn(failed(new NotLeaderOrFollowerException("leader election in progress")));

    publisher.publishPending();

    assertThat(event.isPending()).isTrue();
    assertThat(event.getFailedAt()).isNull();
    assertThat(event.getAttempts()).isEqualTo(51);
  }

  @Test
  void definitiveFailureBelowTheLimitStopsTheBatchWithoutAbandoning() {
    OutboxEventEntity first = pending("orders", "k");
    OutboxEventEntity second = pending("orders", "k");
    when(repository.lockPendingBatch(BATCH)).thenReturn(List.of(first, second));
    when(kafka.send(any(ProducerRecord.class)))
        .thenReturn(failed(new RecordTooLargeException("The message is 2 MB")));

    assertThat(publisher.publishPending()).isZero();

    sentRecords(1);
    assertThat(first.isPending()).isTrue();
    assertThat(first.getAttempts()).isEqualTo(1);
    assertThat(first.getLastError()).startsWith(RecordTooLargeException.class.getName());
    assertThat(second.getAttempts()).isZero();
  }

  @Test
  void definitiveFailureAtTheLimitAbandonsTheEventAndContinuesTheBatch() {
    OutboxEventEntity doomed = pendingAfterFailures(MAX_ATTEMPTS - 1);
    OutboxEventEntity next = pending("orders", "other-key");
    when(repository.lockPendingBatch(BATCH)).thenReturn(List.of(doomed, next));
    when(kafka.send(any(ProducerRecord.class)))
        .thenReturn(failed(new RecordTooLargeException("The message is 2 MB")))
        .thenReturn(acked());

    assertThat(publisher.publishPending()).isEqualTo(1);

    sentRecords(2);
    assertThat(doomed.getFailedAt()).isEqualTo(NOW);
    assertThat(doomed.getPublishedAt()).isNull();
    assertThat(doomed.getAttempts()).isEqualTo(MAX_ATTEMPTS);
    assertThat(doomed.getLastError()).startsWith(RecordTooLargeException.class.getName());
    assertThat(doomed.isPending()).isFalse();
    assertThat(next.getPublishedAt()).isEqualTo(NOW);
  }

  @Test
  void synchronousSendFailureIsHandledLikeAFailedAcknowledgement() {
    OutboxEventEntity event = pending("orders", "k");
    when(repository.lockPendingBatch(BATCH)).thenReturn(List.of(event));
    when(kafka.send(any(ProducerRecord.class))).thenThrow(new KafkaException("Producer closed"));

    assertThat(publisher.publishPending()).isZero();

    assertThat(event.isPending()).isTrue();
    assertThat(event.getAttempts()).isEqualTo(1);
    assertThat(event.getLastError()).startsWith(KafkaException.class.getName());
  }

  @Test
  void classifiesTransientFailuresAnywhereInTheCauseChain() {
    assertThat(OutboxPublisher.isTransient(new TimeoutException("x"))).isTrue();
    assertThat(
            OutboxPublisher.isTransient(
                new ExecutionException(
                    new KafkaProducerException(
                        new ProducerRecord<>("t", "k", "v"),
                        "Failed",
                        new NotLeaderOrFollowerException("x")))))
        .isTrue();
    assertThat(OutboxPublisher.isTransient(new java.util.concurrent.TimeoutException())).isTrue();
    assertThat(OutboxPublisher.isTransient(new InterruptedException())).isTrue();
    assertThat(OutboxPublisher.isTransient(new RecordTooLargeException("x"))).isFalse();
    assertThat(OutboxPublisher.isTransient(new KafkaException("closed"))).isFalse();
    assertThat(OutboxPublisher.isTransient(new IllegalStateException("bug"))).isFalse();
  }

  @Test
  void rejectsInvalidSettings() {
    JsonMapper mapper = JsonMapper.builder().build();
    Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                new OutboxPublisher(
                    repository,
                    kafka,
                    transactionManager,
                    mapper,
                    clock,
                    0,
                    Duration.ofSeconds(1),
                    5))
        .withMessageContaining("batchSize");
    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                new OutboxPublisher(
                    repository,
                    kafka,
                    transactionManager,
                    mapper,
                    clock,
                    10,
                    Duration.ofSeconds(1),
                    0))
        .withMessageContaining("maxAttempts");
  }

  @Test
  void entityRefusesChangesOnceTerminal() {
    OutboxEventEntity published = pending("orders", "k");
    published.markPublished(NOW);
    OutboxEventEntity abandoned = pending("orders", "k");
    abandoned.abandon(NOW);

    assertThatIllegalStateException().isThrownBy(() -> published.recordFailure("late"));
    assertThatIllegalStateException().isThrownBy(() -> published.abandon(NOW));
    assertThatIllegalStateException().isThrownBy(() -> abandoned.markPublished(NOW));
  }
}
