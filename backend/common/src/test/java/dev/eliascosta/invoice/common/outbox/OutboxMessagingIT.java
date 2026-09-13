package dev.eliascosta.invoice.common.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.awaitility.Awaitility.await;

import dev.eliascosta.invoice.common.error.BusinessRuleException;
import dev.eliascosta.invoice.common.event.EventEnvelope;
import dev.eliascosta.invoice.common.event.EventEnvelopeCodec;
import dev.eliascosta.invoice.common.messaging.KafkaMessagingAutoConfiguration;
import dev.eliascosta.invoice.common.messaging.ProcessedEventCleaner;
import dev.eliascosta.invoice.common.messaging.ProcessedEventEntity;
import dev.eliascosta.invoice.common.messaging.ProcessedEventGuard;
import dev.eliascosta.invoice.common.messaging.ProcessedEventId;
import dev.eliascosta.invoice.common.messaging.ProcessedEventRepository;
import dev.eliascosta.invoice.samplemessaging.OrderEventsListener;
import dev.eliascosta.invoice.samplemessaging.OrderEventsListener.OrderPlaced;
import dev.eliascosta.invoice.samplemessaging.SampleMessagingApplication;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import org.apache.kafka.clients.admin.TopicDescription;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ListenerExecutionFailedException;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.json.JsonMapper;

/**
 * End-to-end contract of the transactional outbox, the consumer-side deduplication and the Kafka
 * base configuration (RNF-ARC-05, RNF-RES-02, RNF-RES-03, RN-17, requisitos.md §8.7/§8.11,
 * modelo-de-dados.md §6.1/§6.2/§12, D-015, D-016, D-017) against a real PostgreSQL with the
 * reference DDL and a real Kafka (KRaft).
 *
 * <p>The application under test is {@link SampleMessagingApplication}, outside {@code
 * dev.eliascosta.invoice.common}: the outbox, the guard, the codec, the error handler and the
 * topics all come from the auto-configurations. The poller runs on its real schedule; tests wait
 * for outcomes with Awaitility and identify records by event id, because the scheduler keeps
 * running between tests.
 */
@SpringBootTest(
    classes = SampleMessagingApplication.class,
    properties = {
      "spring.jpa.hibernate.ddl-auto=validate",
      "spring.jpa.open-in-view=false",
      "spring.jpa.properties.hibernate.jdbc.time_zone=UTC",
      "spring.kafka.consumer.auto-offset-reset=earliest",
      "invoice.kafka.topics[0].name=" + OrderEventsListener.TOPIC,
      "invoice.kafka.topics[0].partitions=3",
      // RNF-RES-02 shape (5 attempts, ×2) with a short first delay so the DLT tests stay fast
      "invoice.kafka.consumer.retry.initial-interval=20ms",
      "invoice.outbox.cleanup.batch-size=2",
      "invoice.processed-events.cleanup.batch-size=2"
    })
@Testcontainers
class OutboxMessagingIT {

  @Container @ServiceConnection
  static final PostgreSQLContainer postgres =
      new PostgreSQLContainer("postgres:16-alpine").withInitScript("db/technical_tables.sql");

  @Container @ServiceConnection
  static final KafkaContainer kafka = new KafkaContainer("apache/kafka-native:4.2.1");

  private static final String TOPIC = OrderEventsListener.TOPIC;
  private static final String DLT = TOPIC + KafkaMessagingAutoConfiguration.DEAD_LETTER_SUFFIX;
  private static final String GROUP = OrderEventsListener.GROUP;
  private static final String EVENT_TYPE = "sample.order-placed";
  private static final String SOURCE = "sample-service";
  private static final Duration TIMEOUT = Duration.ofSeconds(20);
  private static final JsonMapper JSON = JsonMapper.builder().build();

  @Autowired private OutboxWriter writer;
  @Autowired private OutboxEventRepository outbox;
  @Autowired private OutboxCleaner outboxCleaner;
  @Autowired private OutboxMetrics metrics;
  @Autowired private ProcessedEventRepository processed;
  @Autowired private ProcessedEventGuard guard;
  @Autowired private ProcessedEventCleaner processedCleaner;
  @Autowired private EventEnvelopeCodec codec;
  @Autowired private OrderEventsListener listener;
  @Autowired private KafkaTemplate<String, String> kafkaTemplate;
  @Autowired private KafkaAdmin kafkaAdmin;
  @Autowired private EntityManager entityManager;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private Clock clock;

  private TransactionTemplate tx;
  private TransactionTemplate txNew;

  @Autowired
  void transactions(PlatformTransactionManager transactionManager) {
    tx = new TransactionTemplate(transactionManager);
    txNew = new TransactionTemplate(transactionManager);
    txNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
  }

  @BeforeEach
  void cleanSlate() {
    outbox.deleteAll();
    processed.deleteAll();
    listener.reset();
  }

  // --- helpers ---------------------------------------------------------------------------------

  private static EventEnvelope<OrderPlaced> event(String customer) {
    UUID orderId = UUID.randomUUID();
    return EventEnvelope.of(
        EVENT_TYPE, SOURCE, orderId.toString(), new OrderPlaced(orderId, customer, 1));
  }

  private void write(EventEnvelope<OrderPlaced> event) {
    write(TOPIC, event);
  }

  private void write(String topic, EventEnvelope<OrderPlaced> event) {
    tx.executeWithoutResult(
        status ->
            writer.write(topic, event.subject(), "Order", UUID.fromString(event.subject()), event));
  }

  private OutboxEventEntity row(UUID eventId) {
    return outbox.findById(eventId).orElseThrow(() -> new AssertionError("no row " + eventId));
  }

  private static @Nullable String header(ConsumerRecord<String, String> record, String name) {
    Header header = record.headers().lastHeader(name);
    return header == null ? null : new String(header.value(), StandardCharsets.UTF_8);
  }

  private Stream<ConsumerRecord<String, String>> receivedFor(UUID eventId) {
    return listener.received().stream()
        .filter(record -> eventId.toString().equals(header(record, OutboxWriter.EVENT_ID_HEADER)));
  }

  private List<EventEnvelope<OrderPlaced>> appliedFor(UUID eventId) {
    return listener.applied().stream().filter(e -> e.id().equals(eventId)).toList();
  }

  private void awaitApplied(UUID eventId) {
    await().atMost(TIMEOUT).untilAsserted(() -> assertThat(appliedFor(eventId)).hasSize(1));
  }

  /** Reads {@code sample.orders.DLT} from the beginning until the record of the event shows up. */
  private ConsumerRecord<String, String> awaitDeadLetter(UUID eventId) {
    Map<String, Object> config =
        Map.of(
            ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
            kafka.getBootstrapServers(),
            ConsumerConfig.GROUP_ID_CONFIG,
            "dlt-probe-" + UUID.randomUUID(),
            ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,
            "earliest",
            ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
            StringDeserializer.class,
            ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
            StringDeserializer.class);
    try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(config)) {
      consumer.subscribe(List.of(DLT));
      List<ConsumerRecord<String, String>> seen = new ArrayList<>();
      return await()
          .atMost(TIMEOUT)
          .until(
              () -> {
                consumer.poll(Duration.ofMillis(200)).forEach(seen::add);
                return seen.stream()
                    .filter(r -> eventId.toString().equals(header(r, OutboxWriter.EVENT_ID_HEADER)))
                    .findFirst();
              },
              Optional::isPresent)
          .orElseThrow();
    }
  }

  private void insertOutboxRow(
      UUID id, String topic, @Nullable Instant publishedAt, @Nullable Instant failedAt) {
    jdbc.update(
        """
        INSERT INTO outbox_events
            (id, aggregate_type, aggregate_id, event_type, topic, partition_key,
             payload, headers, attempts, created_at, published_at, failed_at)
        VALUES (?, 'Order', ?, 'sample.order-placed', ?, 'k', '{}'::jsonb, '{}'::jsonb, 0, now(), ?, ?)
        """,
        id,
        id,
        topic,
        publishedAt == null ? null : Timestamp.from(publishedAt),
        failedAt == null ? null : Timestamp.from(failedAt));
  }

  // --- tests -----------------------------------------------------------------------------------

  @Nested
  class Publishing {

    @Test
    void publishesWrittenEventWithKeyHeadersAndVerbatimEnvelope() {
      EventEnvelope<OrderPlaced> event = event("acme");

      write(event);

      awaitApplied(event.id());
      ConsumerRecord<String, String> record = receivedFor(event.id()).findFirst().orElseThrow();
      assertThat(record.topic()).isEqualTo(TOPIC);
      assertThat(record.key()).isEqualTo(event.subject());
      assertThat(header(record, OutboxWriter.EVENT_TYPE_HEADER)).isEqualTo(EVENT_TYPE);
      assertThat(header(record, OutboxWriter.EVENT_ID_HEADER)).isEqualTo(event.id().toString());
      assertThat(JSON.readTree(record.value())).isEqualTo(JSON.readTree(codec.toJson(event)));
      assertThat(appliedFor(event.id()).getFirst().data()).isEqualTo(event.data());

      OutboxEventEntity row = row(event.id());
      assertThat(row.getPublishedAt()).isNotNull();
      assertThat(row.getFailedAt()).isNull();
      assertThat(row.getAttempts()).isEqualTo(1);
      assertThat(row.getLastError()).isNull();
      assertThat(row.getAggregateType()).isEqualTo("Order");
      assertThat(row.getAggregateId()).isEqualTo(UUID.fromString(event.subject()));
      assertThat(row.getEventType()).isEqualTo(EVENT_TYPE);
      assertThat(row.getPartitionKey()).isEqualTo(event.subject());
      assertThat(JSON.readTree(row.getHeaders()))
          .isEqualTo(
              JSON.readTree(
                  "{\"event-type\":\"" + EVENT_TYPE + "\",\"event-id\":\"" + event.id() + "\"}"));
    }

    @Test
    void writeOutsideTransactionIsRejected() {
      EventEnvelope<OrderPlaced> event = event("acme");

      assertThatIllegalStateException()
          .isThrownBy(
              () ->
                  writer.write(
                      TOPIC, event.subject(), "Order", UUID.fromString(event.subject()), event))
          .withMessageContaining("RNF-ARC-05");

      assertThat(outbox.findById(event.id())).isEmpty();
    }

    @Test
    void rolledBackTransactionLeavesNoEvent() {
      EventEnvelope<OrderPlaced> event = event("acme");

      tx.executeWithoutResult(
          status -> {
            writer.write(TOPIC, event.subject(), "Order", UUID.fromString(event.subject()), event);
            status.setRollbackOnly();
          });

      assertThat(outbox.findById(event.id())).isEmpty();
      await()
          .during(Duration.ofMillis(1500))
          .atMost(Duration.ofSeconds(5))
          .until(() -> receivedFor(event.id()).findAny().isEmpty());
    }

    @Test
    void preservesOrderPerKey() {
      UUID orderId = UUID.randomUUID();
      List<EventEnvelope<OrderPlaced>> events = new ArrayList<>();
      for (int quantity = 1; quantity <= 5; quantity++) {
        EventEnvelope<OrderPlaced> event =
            EventEnvelope.of(
                EVENT_TYPE, SOURCE, orderId.toString(), new OrderPlaced(orderId, "acme", quantity));
        events.add(event);
        write(event); // one transaction each: distinct created_at, like one event per use case
      }

      await()
          .atMost(TIMEOUT)
          .untilAsserted(
              () ->
                  assertThat(listener.applied())
                      .filteredOn(e -> e.subject().equals(orderId.toString()))
                      .hasSize(5));

      assertThat(listener.applied())
          .filteredOn(e -> e.subject().equals(orderId.toString()))
          .extracting(e -> e.data().quantity())
          .containsExactly(1, 2, 3, 4, 5);
      assertThat(
              events.stream()
                  .map(e -> receivedFor(e.id()).findFirst().orElseThrow().partition())
                  .distinct())
          .hasSize(1); // same key → same partition
    }

    /**
     * A row locked by another poller instance is skipped, not waited for: while this transaction
     * holds one pending row, the scheduled poller keeps publishing the others (§11.3).
     */
    @Test
    void skipLockedLetsTheScheduledPollerPublishAroundALockedRow() {
      UUID lockedId =
          tx.execute(
              status -> {
                OutboxEventEntity locked = lockFreshPendingRow();
                EventEnvelope<OrderPlaced> other = event("acme");
                txNew.executeWithoutResult(s -> write(other));

                awaitApplied(other.id()); // the poller was not blocked by our lock
                assertThat(receivedFor(locked.getId())).isEmpty();
                Instant publishedAt =
                    txNew.execute(s -> row(locked.getId()).getPublishedAt()); // fresh snapshot
                assertThat(publishedAt).isNull();
                return locked.getId();
              });

      awaitApplied(lockedId); // lock released with the commit: the next tick takes it
    }

    /**
     * Writes a row in its own transaction and locks it here; retries if the poller got it first.
     */
    private OutboxEventEntity lockFreshPendingRow() {
      for (int attempt = 0; attempt < 5; attempt++) {
        EventEnvelope<OrderPlaced> event = event("acme");
        txNew.executeWithoutResult(s -> write(event));
        OutboxEventEntity row =
            entityManager.find(OutboxEventEntity.class, event.id(), LockModeType.PESSIMISTIC_WRITE);
        if (row.isPending()) {
          return row;
        }
      }
      throw new AssertionError("the poller won the race five times in a row");
    }

    /** D-015: an invalid topic name is a definitive failure — abandoned after 5 attempts. */
    @Test
    void abandonsEventAfterMaxDefinitiveFailuresAndKeepsPublishingTheRest() {
      EventEnvelope<OrderPlaced> doomed = event("acme");
      write("invalid topic name", doomed); // spaces are illegal → InvalidTopicException

      await()
          .atMost(TIMEOUT)
          .untilAsserted(() -> assertThat(row(doomed.id()).getFailedAt()).isNotNull());
      OutboxEventEntity row = row(doomed.id());
      assertThat(row.getPublishedAt()).isNull();
      assertThat(row.getAttempts()).isEqualTo(5);
      assertThat(row.getLastError()).contains("InvalidTopicException");
      assertThat(receivedFor(doomed.id())).isEmpty();

      EventEnvelope<OrderPlaced> next = event("acme");
      write(next);
      awaitApplied(next.id()); // the abandoned row no longer blocks the queue

      SimpleMeterRegistry registry = new SimpleMeterRegistry();
      metrics.bindTo(registry);
      assertThat(registry.get(OutboxMetrics.FAILED_EVENTS).gauge().value()).isEqualTo(1.0);
      assertThat(registry.get(OutboxMetrics.PENDING_EVENTS).gauge().value()).isEqualTo(0.0);
    }

    @Test
    void pendingGaugeCountsUnpublishedRows() {
      SimpleMeterRegistry registry = new SimpleMeterRegistry();
      metrics.bindTo(registry);
      assertThat(registry.get(OutboxMetrics.PENDING_EVENTS).gauge().value()).isEqualTo(0.0);

      // sampled inside the writing transaction: the rows are visible here and to nobody else yet
      tx.executeWithoutResult(
          status -> {
            write(event("acme"));
            write(event("acme"));
            assertThat(registry.get(OutboxMetrics.PENDING_EVENTS).gauge().value()).isEqualTo(2.0);
            status.setRollbackOnly();
          });

      assertThat(registry.get(OutboxMetrics.PENDING_EVENTS).gauge().value()).isEqualTo(0.0);
    }
  }

  @Nested
  class Consuming {

    @Test
    void duplicateDeliveryIsAppliedOnce() throws Exception {
      EventEnvelope<OrderPlaced> event = event("acme");
      String json = codec.toJson(event);
      // Delivered twice straight to the topic: the outbox primary key forbids two rows per event.
      kafkaTemplate.send(TOPIC, event.subject(), json).get();
      kafkaTemplate.send(TOPIC, event.subject(), json).get();

      await()
          .atMost(TIMEOUT)
          .untilAsserted(() -> assertThat(listener.deliveries(event.id())).isEqualTo(2));

      assertThat(appliedFor(event.id())).hasSize(1);
      assertThat(listener.deliveries(event.id())).isEqualTo(2);
      assertThat(processed.findById(new ProcessedEventId(event.id(), GROUP)))
          .get()
          .extracting(ProcessedEventEntity::getEventType)
          .isEqualTo(EVENT_TYPE);
    }

    @Test
    void failedEffectRollsBackTheMarkSoTheRedeliveryIsProcessed() {
      EventEnvelope<OrderPlaced> event = event(OrderEventsListener.FLAKY);

      write(event);

      awaitApplied(event.id());
      assertThat(listener.deliveries(event.id())).isEqualTo(2);
      assertThat(processed.findById(new ProcessedEventId(event.id(), GROUP))).isPresent();
    }

    /** RNF-RES-02: 5 attempts, then {@code <topic>.DLT} with the exception headers. */
    @Test
    void poisonRecordGoesToDeadLetterTopicAfterFiveAttempts() {
      EventEnvelope<OrderPlaced> event = event(OrderEventsListener.POISON);

      write(event);

      ConsumerRecord<String, String> dead = awaitDeadLetter(event.id());
      assertThat(listener.deliveries(event.id())).isEqualTo(5);
      assertThat(dead.key()).isEqualTo(event.subject());
      assertThat(JSON.readTree(dead.value())).isEqualTo(JSON.readTree(codec.toJson(event)));
      assertThat(header(dead, KafkaHeaders.DLT_ORIGINAL_TOPIC)).isEqualTo(TOPIC);
      assertThat(header(dead, KafkaHeaders.DLT_EXCEPTION_FQCN))
          .isEqualTo(ListenerExecutionFailedException.class.getName());
      assertThat(header(dead, KafkaHeaders.DLT_EXCEPTION_CAUSE_FQCN))
          .isEqualTo(IllegalStateException.class.getName());
      assertThat(appliedFor(event.id())).isEmpty();
      assertThat(processed.findById(new ProcessedEventId(event.id(), GROUP))).isEmpty();
    }

    /** D-016: a business rule that escapes the listener is not retried. */
    @Test
    void businessRuleExceptionGoesToDeadLetterTopicWithoutRetries() {
      EventEnvelope<OrderPlaced> event = event(OrderEventsListener.REJECTED);

      write(event);

      ConsumerRecord<String, String> dead = awaitDeadLetter(event.id());
      assertThat(listener.deliveries(event.id())).isEqualTo(1);
      assertThat(header(dead, KafkaHeaders.DLT_EXCEPTION_CAUSE_FQCN))
          .isEqualTo(BusinessRuleException.class.getName());
      assertThat(processed.findById(new ProcessedEventId(event.id(), GROUP))).isEmpty();
    }

    @Test
    void guardRequiresAnActiveTransaction() {
      assertThatIllegalStateException()
          .isThrownBy(() -> guard.tryMark(UUID.randomUUID(), EVENT_TYPE, GROUP))
          .withMessageContaining("RNF-RES-03");
    }

    @Test
    void guardMarksOncePerGroupAndForgetsOnRollback() {
      UUID eventId = UUID.randomUUID();

      tx.executeWithoutResult(
          status -> {
            assertThat(guard.tryMark(eventId, EVENT_TYPE, GROUP)).isTrue();
            assertThat(guard.tryMark(eventId, EVENT_TYPE, GROUP)).isFalse();
            status.setRollbackOnly();
          });
      assertThat(processed.findById(new ProcessedEventId(eventId, GROUP))).isEmpty();

      Boolean firstAgain = tx.execute(status -> guard.tryMark(eventId, EVENT_TYPE, GROUP));
      Boolean duplicate = tx.execute(status -> guard.tryMark(eventId, EVENT_TYPE, GROUP));
      Boolean otherGroup = tx.execute(status -> guard.tryMark(eventId, EVENT_TYPE, "other-group"));
      assertThat(firstAgain).isTrue(); // the rolled-back mark is gone
      assertThat(duplicate).isFalse();
      assertThat(otherGroup).isTrue(); // scoped by consumer group
      assertThat(processed.count()).isEqualTo(2);
    }
  }

  @Nested
  class Topics {

    /** D-017: declared topics get their partitions, dead-letter companions get one. */
    @Test
    void createsDeclaredTopicsAndTheirDeadLetterCompanions() {
      Map<String, TopicDescription> topics = kafkaAdmin.describeTopics(TOPIC, DLT);

      assertThat(topics.get(TOPIC).partitions()).hasSize(3);
      assertThat(topics.get(DLT).partitions()).hasSize(1);
    }
  }

  @Nested
  class Cleanup {

    @Test
    void outboxCleanerDeletesOnlyEventsPublishedBeyondRetention() {
      Instant now = clock.instant();
      UUID old1 = UUID.randomUUID();
      UUID old2 = UUID.randomUUID();
      UUID old3 = UUID.randomUUID();
      UUID recent = UUID.randomUUID();
      UUID abandoned = UUID.randomUUID();
      insertOutboxRow(old1, TOPIC, now.minus(Duration.ofDays(8)), null);
      insertOutboxRow(old2, TOPIC, now.minus(Duration.ofDays(30)), null);
      insertOutboxRow(old3, TOPIC, now.minus(Duration.ofDays(7)).minusSeconds(1), null);
      insertOutboxRow(recent, TOPIC, now.minus(Duration.ofDays(6)), null);
      insertOutboxRow(abandoned, TOPIC, null, now.minus(Duration.ofDays(30)));

      int deleted = outboxCleaner.purgePublished(); // batch size 2 → three batches (2, 1, 0)

      assertThat(deleted).isEqualTo(3);
      assertThat(outbox.findAllById(List.of(old1, old2, old3))).isEmpty();
      assertThat(outbox.findAllById(List.of(recent, abandoned))).hasSize(2);
    }

    @Test
    void processedEventCleanerDeletesOnlyMarksBeyondRetention() {
      Instant now = clock.instant();
      tx.executeWithoutResult(
          status -> {
            for (int i = 0; i < 3; i++) {
              processed.insertIfAbsent(
                  UUID.randomUUID(), GROUP, EVENT_TYPE, now.minus(Duration.ofDays(31)));
            }
            processed.insertIfAbsent(
                UUID.randomUUID(), GROUP, EVENT_TYPE, now.minus(Duration.ofDays(29)));
          });

      int deleted = processedCleaner.purgeProcessed(); // batch size 2 → three batches

      assertThat(deleted).isEqualTo(3);
      assertThat(processed.count()).isEqualTo(1);
    }
  }
}
