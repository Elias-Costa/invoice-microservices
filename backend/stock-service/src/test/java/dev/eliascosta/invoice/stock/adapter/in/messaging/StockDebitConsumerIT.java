package dev.eliascosta.invoice.stock.adapter.in.messaging;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doAnswer;

import dev.eliascosta.invoice.common.event.EventEnvelope;
import dev.eliascosta.invoice.common.event.EventEnvelopeCodec;
import dev.eliascosta.invoice.common.outbox.OutboxWriter;
import dev.eliascosta.invoice.stock.domain.model.Product;
import dev.eliascosta.invoice.stock.domain.model.Quantity;
import dev.eliascosta.invoice.stock.domain.port.in.DebitStockUseCase;
import dev.eliascosta.invoice.stock.domain.port.in.DebitStockUseCase.Command;
import dev.eliascosta.invoice.stock.domain.port.in.DebitStockUseCase.Item;
import dev.eliascosta.invoice.stock.domain.port.out.ProductRepository;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class StockDebitConsumerIT {

  private static final String REQUESTED = "stock.debit-requested";
  private static final String CONFIRMED = "stock.debit-confirmed";
  private static final String REJECTED = "stock.debit-rejected";
  private static final String REQUESTED_DLT = REQUESTED + ".DLT";
  private static final long INVOICE_NUMBER = 42L;
  private static final String REQUESTED_BY = "operator";

  @Container @ServiceConnection
  static final PostgreSQLContainer postgres =
      new PostgreSQLContainer("postgres:16-alpine").withDatabaseName("stock_db");

  @Container @ServiceConnection
  static final KafkaContainer kafka = new KafkaContainer("apache/kafka-native:4.2.1");

  @Autowired private JdbcTemplate jdbc;
  @Autowired private JsonMapper json;
  @Autowired private EventEnvelopeCodec codec;
  @Autowired private KafkaTemplate<String, String> producer;
  @Autowired private DebitStockUseCase debitStock;
  @MockitoSpyBean private ProductRepository products;

  @Test
  void confirmsEveryItemAndPersistsMovementsAndTheSagaResult() throws Exception {
    UUID first = product("DEBIT-A", 10, true);
    UUID second = product("DEBIT-B", 5, true);
    var request = request(new RequestedItem(first, 2), new RequestedItem(second, 3));

    send(request);
    JsonNode data = result(CONFIRMED, request);

    assertThat(data.path("items"))
        .containsExactly(
            json.valueToTree(Map.of("productId", first, "quantity", 2, "balanceAfter", 8)),
            json.valueToTree(Map.of("productId", second, "quantity", 3, "balanceAfter", 2)));
    assertThat(balance(first)).isEqualTo(8);
    assertThat(balance(second)).isEqualTo(2);
    assertThat(
            jdbc.queryForList(
                """
                SELECT product_id, quantity, balance_after, reference_number, created_by
                FROM stock_movements
                WHERE reference_id = ? AND type = 'INVOICE_DEBIT'
                """,
                request.data().invoiceId()))
        .containsExactlyInAnyOrder(
            Map.of(
                "product_id", first,
                "quantity", 2,
                "balance_after", 8,
                "reference_number", INVOICE_NUMBER,
                "created_by", REQUESTED_BY),
            Map.of(
                "product_id", second,
                "quantity", 3,
                "balance_after", 2,
                "reference_number", INVOICE_NUMBER,
                "created_by", REQUESTED_BY));
    assertStored(request, "CONFIRMED", null, data.path("items"));
  }

  @Test
  void rejectsTheWholeNoteAndReportsAllInsufficientItems() throws Exception {
    UUID enough = product("ENOUGH", 10, true);
    UUID low = product("LOW", 1, true);
    UUID empty = product("EMPTY", 0, true);
    var request =
        request(
            new RequestedItem(enough, 2), new RequestedItem(low, 3), new RequestedItem(empty, 1));

    send(request);
    JsonNode data = result(REJECTED, request);

    assertThat(data.path("reason").asString()).isEqualTo("INSUFFICIENT_STOCK");
    assertThat(data.path("details"))
        .containsExactly(
            json.valueToTree(
                Map.of("productId", low, "productCode", "LOW", "requested", 3, "available", 1)),
            json.valueToTree(
                Map.of(
                    "productId", empty, "productCode", "EMPTY", "requested", 1, "available", 0)));
    assertThat(balance(enough)).isEqualTo(10);
    assertThat(balance(low)).isEqualTo(1);
    assertThat(balance(empty)).isZero();
    assertThat(debitMovements(request)).isZero();
    assertStored(request, "REJECTED", "INSUFFICIENT_STOCK", data.path("details"));
  }

  @Test
  void prioritizesInactiveOverInsufficientStock() throws Exception {
    UUID low = product("LOW-INACTIVE-CASE", 0, true);
    UUID inactive = product("INACTIVE", 10, false);
    var request = request(new RequestedItem(low, 1), new RequestedItem(inactive, 2));

    send(request);
    JsonNode data = result(REJECTED, request);

    assertThat(data.path("reason").asString()).isEqualTo("PRODUCT_INACTIVE");
    assertThat(data.path("details"))
        .containsExactly(
            json.valueToTree(
                Map.of(
                    "productId",
                    low,
                    "productCode",
                    "LOW-INACTIVE-CASE",
                    "requested",
                    1,
                    "available",
                    0)),
            json.valueToTree(
                Map.of(
                    "productId",
                    inactive,
                    "productCode",
                    "INACTIVE",
                    "requested",
                    2,
                    "available",
                    10)));
    assertThat(balance(inactive)).isEqualTo(10);
    assertThat(debitMovements(request)).isZero();
  }

  @Test
  void prioritizesMissingProductAndReportsNullForUnknownValues() throws Exception {
    UUID inactive = product("INACTIVE-MISSING-CASE", 10, false);
    UUID missing = UUID.randomUUID();
    var request = request(new RequestedItem(inactive, 2), new RequestedItem(missing, 1));

    send(request);
    JsonNode data = result(REJECTED, request);

    assertThat(data.path("reason").asString()).isEqualTo("PRODUCT_NOT_FOUND");
    assertThat(data.path("details")).hasSize(2);
    JsonNode missingDetail = data.path("details").get(1);
    assertThat(missingDetail.path("productId").asString()).isEqualTo(missing.toString());
    assertThat(missingDetail.path("requested").asInt()).isEqualTo(1);
    assertThat(missingDetail.path("productCode").isNull()).isTrue();
    assertThat(missingDetail.path("available").isNull()).isTrue();
    assertThat(balance(inactive)).isEqualTo(10);
    assertThat(debitMovements(request)).isZero();
  }

  @Test
  void skipsTheSameEventAndRepublishesTheStoredConfirmationForANewEventId() throws Exception {
    UUID product = product("REPLAY-CONFIRMED", 10, true);
    var request = request(new RequestedItem(product, 2));
    var republished =
        EventEnvelope.of(REQUESTED, "billing-service", request.subject(), request.data());

    send(request);
    send(request);
    send(republished);
    var records = receive(CONFIRMED, request, 2);

    assertThat(records).hasSize(2);
    JsonNode first = resultData(records.getFirst(), request);
    JsonNode second = resultData(records.getLast(), republished);
    assertThat(second).isEqualTo(first);
    assertThat(json.readTree(records.getFirst().value()).path("id"))
        .isNotEqualTo(json.readTree(records.getLast().value()).path("id"));
    assertThat(outboxCount(request)).isEqualTo(2);
    assertThat(processed(request.id())).isOne();
    assertThat(processed(republished.id())).isOne();
    assertThat(debitMovements(request)).isOne();
    assertThat(balance(product)).isEqualTo(8);
    assertStored(request, "CONFIRMED", null, first.path("items"));
  }

  @Test
  void republishesTheStoredRejectionAndCanConfirmANewAttemptAfterRestocking() throws Exception {
    UUID product = product("REPLAY-REJECTED", 1, true);
    var request = request(new RequestedItem(product, 2));

    send(request);
    JsonNode rejected = result(REJECTED, request);
    jdbc.update("UPDATE products SET balance = 10, version = version + 1 WHERE id = ?", product);
    var republished =
        EventEnvelope.of(REQUESTED, "billing-service", request.subject(), request.data());
    send(republished);
    var results = receive(REJECTED, request, 2);
    assertThat(resultData(results.getLast(), republished)).isEqualTo(rejected);
    assertThat(balance(product)).isEqualTo(10);
    assertThat(debitMovements(request)).isZero();
    assertThat(processed(republished.id())).isOne();
    assertStored(request, "REJECTED", "INSUFFICIENT_STOCK", rejected.path("details"));

    var nextAttempt = newAttempt(request);
    send(nextAttempt);
    JsonNode confirmed = result(CONFIRMED, nextAttempt);
    assertStored(nextAttempt, "CONFIRMED", null, confirmed.path("items"));
    assertThat(balance(product)).isEqualTo(8);
    assertThat(debitMovements(request)).isOne();
  }

  @Test
  void sendsANewAttemptForAnAlreadyConfirmedInvoiceToTheDeadLetterTopic() throws Exception {
    UUID product = product("ALREADY-CONFIRMED", 10, true);
    var request = request(new RequestedItem(product, 2));
    send(request);
    result(CONFIRMED, request);

    var invalidAttempt = newAttempt(request);
    send(invalidAttempt);
    var deadLetter = receive(REQUESTED_DLT, invalidAttempt, 1).getFirst();

    assertThat(deadLetter.value()).isEqualTo(codec.toJson(invalidAttempt));
    assertThat(header(deadLetter, "kafka_dlt-exception-cause-fqcn"))
        .isEqualTo(IllegalStateException.class.getName());
    assertThat(processed(invalidAttempt.id())).isZero();
    assertThat(outboxCount(request)).isOne();
    assertThat(debitMovements(request)).isOne();
    assertThat(balance(product)).isEqualTo(8);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM invoice_debits WHERE print_request_id = ?",
                Integer.class,
                invalidAttempt.data().printRequestId()))
        .isZero();
  }

  @Test
  void retriesInAFreshTransactionAfterAnOptimisticLockConflict() {
    UUID first = UUID.fromString("00000000-0000-4000-8000-000000000001");
    UUID second = UUID.fromString("00000000-0000-4000-8000-000000000002");
    insertProduct(first, "RETRY-FIRST", 10, true);
    insertProduct(second, "RETRY-SECOND", 5, true);
    var request = request(new RequestedItem(first, 2), new RequestedItem(second, 3));
    AtomicInteger writes = new AtomicInteger();
    doAnswer(
            invocation -> {
              assertThat(balance(first)).isEqualTo(8);
              if (writes.incrementAndGet() == 1) {
                throw new ObjectOptimisticLockingFailureException(Product.class, second);
              }
              return invocation.callRealMethod();
            })
        .when(products)
        .save(argThat(product -> product != null && second.equals(product.getId())));

    // Call the use case directly to distinguish local retry from Kafka redelivery.
    debitStock.debit(
        new Command(
            request.id(),
            request.data().printRequestId(),
            request.data().invoiceId(),
            INVOICE_NUMBER,
            REQUESTED_BY,
            List.of(new Item(first, new Quantity(2)), new Item(second, new Quantity(3)))));
    JsonNode data = result(CONFIRMED, request);

    assertThat(writes.get()).isEqualTo(2);
    assertThat(balance(first)).isEqualTo(8);
    assertThat(balance(second)).isEqualTo(2);
    assertThat(debitMovements(request)).isEqualTo(2);
    assertThat(outboxCount(request)).isOne();
    assertStored(request, "CONFIRMED", null, data.path("items"));
  }

  private void assertStored(
      EventEnvelope<Requested> request, String outcome, String reason, JsonNode details) {
    assertThat(processed(request.id())).isOne();
    var debit =
        jdbc.queryForMap(
            """
            SELECT invoice_id, invoice_number, request_event_id, outcome, reason, details::text
            FROM invoice_debits WHERE print_request_id = ?
            """,
            request.data().printRequestId());
    assertThat(debit)
        .containsEntry("invoice_id", request.data().invoiceId())
        .containsEntry("invoice_number", INVOICE_NUMBER)
        .containsEntry("request_event_id", request.id())
        .containsEntry("outcome", outcome)
        .containsEntry("reason", reason);
    assertThat(json.readTree((String) debit.get("details"))).isEqualTo(details);
  }

  private UUID product(String code, int balance, boolean active) {
    UUID id = UUID.randomUUID();
    insertProduct(id, code, balance, active);
    return id;
  }

  private void insertProduct(UUID id, String code, int balance, boolean active) {
    jdbc.update(
        """
        INSERT INTO products (id, code, description, unit_price, balance, active, created_by)
        VALUES (?, ?, 'Test product', 1.00, ?, ?, 'test')
        """,
        id,
        code,
        balance,
        active);
  }

  private int balance(UUID productId) {
    return jdbc.queryForObject(
        "SELECT balance FROM products WHERE id = ?", Integer.class, productId);
  }

  private int debitMovements(EventEnvelope<Requested> request) {
    return jdbc.queryForObject(
        "SELECT count(*) FROM stock_movements WHERE reference_id = ? AND type = 'INVOICE_DEBIT'",
        Integer.class,
        request.data().invoiceId());
  }

  private int outboxCount(EventEnvelope<Requested> request) {
    return jdbc.queryForObject(
        "SELECT count(*) FROM outbox_events WHERE aggregate_id = ?",
        Integer.class,
        request.data().invoiceId());
  }

  private int processed(UUID eventId) {
    return jdbc.queryForObject(
        """
        SELECT count(*) FROM processed_events
        WHERE event_id = ? AND consumer_group = 'stock-service' AND event_type = ?
        """,
        Integer.class,
        eventId,
        REQUESTED);
  }

  private EventEnvelope<Requested> request(RequestedItem... items) {
    UUID invoice = UUID.randomUUID();
    return EventEnvelope.of(
        REQUESTED,
        "billing-service",
        invoice.toString(),
        new Requested(UUID.randomUUID(), invoice, INVOICE_NUMBER, REQUESTED_BY, List.of(items)));
  }

  private EventEnvelope<Requested> newAttempt(EventEnvelope<Requested> request) {
    Requested original = request.data();
    return EventEnvelope.of(
        REQUESTED,
        "billing-service",
        request.subject(),
        new Requested(
            UUID.randomUUID(),
            original.invoiceId(),
            original.invoiceNumber(),
            original.requestedBy(),
            original.items()));
  }

  private void send(EventEnvelope<Requested> request) throws Exception {
    ProducerRecord<String, String> record =
        new ProducerRecord<>(REQUESTED, request.subject(), codec.toJson(request));
    record.headers().add(OutboxWriter.EVENT_ID_HEADER, request.id().toString().getBytes(UTF_8));
    record.headers().add(OutboxWriter.EVENT_TYPE_HEADER, REQUESTED.getBytes(UTF_8));
    producer.send(record).get(10, TimeUnit.SECONDS);
  }

  private JsonNode result(String topic, EventEnvelope<Requested> request) {
    return resultData(receive(topic, request, 1).getFirst(), request);
  }

  private JsonNode resultData(
      ConsumerRecord<String, String> record, EventEnvelope<Requested> request) {
    JsonNode envelope = json.readTree(record.value());
    assertThat(envelope.path("type").asString()).isEqualTo(record.topic());
    assertThat(envelope.path("source").asString()).isEqualTo("stock-service");
    assertThat(envelope.path("specversion").asString()).isEqualTo("1.0");
    assertThat(envelope.path("subject").asString()).isEqualTo(request.subject());
    assertThat(header(record, OutboxWriter.EVENT_TYPE_HEADER)).isEqualTo(record.topic());
    assertThat(header(record, OutboxWriter.EVENT_ID_HEADER))
        .isEqualTo(envelope.path("id").asString());
    JsonNode data = envelope.path("data");
    assertThat(data.path("invoiceId").asString()).isEqualTo(request.subject());
    assertThat(data.path("printRequestId").asString())
        .isEqualTo(request.data().printRequestId().toString());
    return data;
  }

  private List<ConsumerRecord<String, String>> receive(
      String topic, EventEnvelope<Requested> request, int count) {
    Map<String, Object> config =
        Map.of(
            ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
            kafka.getBootstrapServers(),
            ConsumerConfig.GROUP_ID_CONFIG,
            "stock-debit-it-" + UUID.randomUUID(),
            ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,
            "earliest",
            ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
            StringDeserializer.class,
            ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
            StringDeserializer.class);
    List<ConsumerRecord<String, String>> matching = new ArrayList<>();
    try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(config)) {
      consumer.subscribe(List.of(topic));
      await()
          .pollInSameThread()
          .atMost(Duration.ofSeconds(40))
          .until(
              () -> {
                for (var record : consumer.poll(Duration.ofMillis(200))) {
                  if (request.subject().equals(record.key())
                      && (!topic.equals(REQUESTED_DLT)
                          || request
                              .id()
                              .toString()
                              .equals(header(record, OutboxWriter.EVENT_ID_HEADER)))) {
                    matching.add(record);
                  }
                }
                return matching.size() >= count;
              });
    }
    return matching;
  }

  private static String header(ConsumerRecord<String, String> record, String name) {
    var header = record.headers().lastHeader(name);
    return header == null ? null : new String(header.value(), UTF_8);
  }

  private record Requested(
      UUID printRequestId,
      UUID invoiceId,
      long invoiceNumber,
      String requestedBy,
      List<RequestedItem> items) {}

  private record RequestedItem(UUID productId, int quantity) {}
}
