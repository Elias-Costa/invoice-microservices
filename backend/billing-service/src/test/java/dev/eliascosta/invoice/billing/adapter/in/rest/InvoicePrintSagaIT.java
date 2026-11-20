package dev.eliascosta.invoice.billing.adapter.in.rest;

import static dev.eliascosta.invoice.billing.adapter.in.rest.InvoiceController.PATH;
import static dev.eliascosta.invoice.common.idempotency.Idempotent.HEADER;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.eliascosta.invoice.billing.domain.model.Customer;
import dev.eliascosta.invoice.billing.domain.model.Document;
import dev.eliascosta.invoice.billing.domain.model.Invoice;
import dev.eliascosta.invoice.billing.domain.model.InvoiceItem;
import dev.eliascosta.invoice.billing.domain.model.InvoiceStatus;
import dev.eliascosta.invoice.billing.domain.model.InvoiceStatusHistory;
import dev.eliascosta.invoice.billing.domain.model.Money;
import dev.eliascosta.invoice.billing.domain.model.Quantity;
import dev.eliascosta.invoice.billing.domain.port.in.InvoiceUseCases;
import dev.eliascosta.invoice.billing.domain.port.out.CustomerRepository;
import dev.eliascosta.invoice.billing.domain.port.out.InvoiceRepository;
import dev.eliascosta.invoice.common.event.EventEnvelope;
import dev.eliascosta.invoice.common.event.EventEnvelopeCodec;
import dev.eliascosta.invoice.common.outbox.OutboxWriter;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class InvoicePrintSagaIT {

  private static final String REQUESTED = "stock.debit-requested";
  private static final String CONFIRMED = "stock.debit-confirmed";
  private static final String REJECTED = "stock.debit-rejected";
  private static final String USER = "anonymous";
  private static final Instant CREATED_AT = Instant.parse("2026-11-19T10:00:00Z");
  private static final Instant CHANGED_AT = CREATED_AT.plusSeconds(1);
  private static final UUID PRODUCT = UUID.fromString("719c2177-db22-4b8c-acfd-8c4002707d34");
  private static final UUID OTHER_PRODUCT = UUID.fromString("f87c5b58-c21c-44dc-8d73-23c960c97283");
  private static final Duration KAFKA_TIMEOUT = Duration.ofSeconds(40);

  @Container @ServiceConnection
  static final PostgreSQLContainer postgres =
      new PostgreSQLContainer("postgres:16-alpine").withDatabaseName("billing_db");

  @Container @ServiceConnection
  static final KafkaContainer kafka = new KafkaContainer("apache/kafka-native:4.2.1");

  private static Customer customer;

  @Autowired private MockMvc mvc;
  @Autowired private JsonMapper json;
  @Autowired private EventEnvelopeCodec codec;
  @Autowired private KafkaTemplate<String, String> kafkaTemplate;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private InvoiceRepository invoices;
  @Autowired private InvoiceUseCases invoiceUseCases;
  @Autowired private TransactionTemplate transactions;
  @Autowired private EntityManager entityManager;

  @BeforeAll
  static void storeCustomer(
      @Autowired CustomerRepository customers, @Autowired TransactionTemplate transactions) {
    customer =
        transactions.execute(
            tx ->
                customers.save(
                    Customer.create(
                        "Cliente", new Document("52998224725"), null, null, USER, CREATED_AT)));
  }

  @Test
  void publishesThePrintRequestClosesOnceAndReplaysTheOriginalHttpResponse() throws Exception {
    Invoice original = storeInvoice(invoice -> {});
    UUID id = original.getId();
    String key = UUID.randomUUID().toString();
    var response =
        print(id, key)
            .andExpect(status().isAccepted())
            .andExpect(header().string("Location", PATH + "/" + id))
            .andReturn()
            .getResponse();
    var accepted = json.readTree(response.getContentAsString());
    assertThat(accepted)
        .isEqualTo(
            json.readTree(
                """
                {"id":"%s","status":"PROCESSING"}
                """
                    .formatted(id)));

    Invoice processing = load(id);
    assertThat(processing.getStatus()).isEqualTo(InvoiceStatus.PROCESSING);
    assertThat(processing.getProcessingStartedAt()).isNotNull();
    assertThat(processing.getHistory().getLast().getChangedBy()).isEqualTo(USER);
    assertThat(processing.getHistory().getLast().getChangedAt())
        .isEqualTo(processing.getProcessingStartedAt());
    assertThat(outboxCount(id)).isEqualTo(1);

    var record = receive(REQUESTED, id);
    var request = codec.fromJson(record.value(), DebitRequested.class);
    assertThat(request.type()).isEqualTo(REQUESTED);
    assertThat(request.subject()).isEqualTo(id.toString());
    assertThat(request.source()).isEqualTo("billing-service");
    assertThat(request.specversion()).isEqualTo("1.0");
    assertThat(record.headers().lastHeader(OutboxWriter.EVENT_ID_HEADER).value())
        .isEqualTo(request.id().toString().getBytes(UTF_8));
    assertThat(record.headers().lastHeader(OutboxWriter.EVENT_TYPE_HEADER).value())
        .isEqualTo(REQUESTED.getBytes(UTF_8));
    assertThat(request.data())
        .isEqualTo(
            new DebitRequested(
                processing.getPrintRequestId(),
                id,
                original.getNumber(),
                USER,
                List.of(new RequestedItem(PRODUCT, 3), new RequestedItem(OTHER_PRODUCT, 2))));

    var confirmation = confirmation(processing.getPrintRequestId(), id);
    send(confirmation);
    awaitProcessed(confirmation);
    Invoice closed = load(id);
    assertThat(closed.getStatus()).isEqualTo(InvoiceStatus.CLOSED);
    assertThat(closed.getPrintRequestId()).isEqualTo(processing.getPrintRequestId());
    assertThat(closed.getProcessingStartedAt()).isNull();
    assertThat(closed.getClosedAt()).isNotNull();
    assertThat(closed.getHistory())
        .extracting(InvoiceStatusHistory::getToStatus)
        .containsExactly(InvoiceStatus.OPEN, InvoiceStatus.PROCESSING, InvoiceStatus.CLOSED);
    assertThat(closed.getHistory().getLast().getChangedBy()).isEqualTo("system");
    assertThat(closed.getHistory().getLast().getChangedAt()).isEqualTo(closed.getClosedAt());

    send(confirmation);
    // The next event on the same partition proves that the duplicate was consumed.
    var afterClose = confirmation(processing.getPrintRequestId(), id);
    send(afterClose);
    awaitProcessed(afterClose);
    assertThat(processedCount(confirmation.id())).isEqualTo(1);
    Invoice afterDuplicate = load(id);
    assertThat(afterDuplicate.getHistory()).hasSize(3);
    assertThat(afterDuplicate.getVersion()).isEqualTo(closed.getVersion());
    assertThat(afterDuplicate.getClosedAt()).isEqualTo(closed.getClosedAt());

    var replay =
        print(id, key)
            .andExpect(status().isAccepted())
            .andExpect(header().string("Location", PATH + "/" + id))
            .andReturn()
            .getResponse();
    assertThat(json.readTree(replay.getContentAsString())).isEqualTo(accepted);
    assertThat(outboxCount(id)).isEqualTo(1);
  }

  @Test
  void reopensOnRejectionIgnoresAResultForTheOldAttemptAndClearsTheReasonOnSuccess()
      throws Exception {
    UUID id = storeInvoice(invoice -> {}).getId();
    print(id, UUID.randomUUID().toString()).andExpect(status().isAccepted());
    UUID firstAttempt = load(id).getPrintRequestId();
    var rejection =
        EventEnvelope.of(
            REJECTED,
            "stock-service",
            id.toString(),
            Map.of(
                "printRequestId",
                firstAttempt,
                "invoiceId",
                id,
                "reason",
                "INSUFFICIENT_STOCK",
                "details",
                List.of(
                    Map.of(
                        "productId",
                        PRODUCT,
                        "productCode",
                        "PARAFUSO-M6",
                        "requested",
                        3,
                        "available",
                        1))));
    send(rejection);
    awaitProcessed(rejection);

    String reason = "Saldo insuficiente para PARAFUSO-M6 (solicitado 3, disponível 1)";
    mvc.perform(get(PATH + "/{id}", id))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("OPEN"))
        .andExpect(jsonPath("$.lastFailureReason").value(reason))
        .andExpect(jsonPath("$.allowedActions", hasItems("EDIT_ITEMS", "PRINT")));
    Invoice reopened = load(id);
    assertThat(reopened.getPrintRequestId()).isEqualTo(firstAttempt);
    assertThat(reopened.getProcessingStartedAt()).isNull();
    assertThat(reopened.getHistory().getLast().getReason()).isEqualTo(reason);
    assertThat(reopened.getHistory().getLast().getChangedBy()).isEqualTo("system");

    print(id, UUID.randomUUID().toString()).andExpect(status().isAccepted());
    Invoice processing = load(id);
    assertThat(processing.getPrintRequestId()).isNotEqualTo(firstAttempt);
    assertThat(processing.getLastFailureReason()).isEqualTo(reason);
    assertThat(outboxCount(id)).isEqualTo(2);

    var staleConfirmation = confirmation(firstAttempt, id);
    send(staleConfirmation);
    awaitProcessed(staleConfirmation);
    Invoice afterStale = load(id);
    assertThat(afterStale.getStatus()).isEqualTo(InvoiceStatus.PROCESSING);
    assertThat(afterStale.getVersion()).isEqualTo(processing.getVersion());
    assertThat(afterStale.getHistory()).hasSize(4);

    var currentConfirmation = confirmation(processing.getPrintRequestId(), id);
    send(currentConfirmation);
    awaitProcessed(currentConfirmation);
    mvc.perform(get(PATH + "/{id}", id))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("CLOSED"))
        .andExpect(jsonPath("$.lastFailureReason").value(nullValue()))
        .andExpect(jsonPath("$.closedAt").isString());
    Invoice closed = load(id);
    assertThat(closed.getLastFailureReason()).isNull();
    assertThat(closed.getHistory()).hasSize(5);
    assertThat(closed.getHistory().get(2).getReason()).isEqualTo(reason);
  }

  @Test
  void rejectsMissingAndMalformedKeysWithoutStartingThePrint() throws Exception {
    Invoice invoice = storeInvoice(candidate -> {});
    UUID id = invoice.getId();
    mvc.perform(post(PATH + "/{id}/print", id))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].field").value(HEADER));
    print(id, "not-a-uuid")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].field").value(HEADER));
    assertThat(load(id).getStatus()).isEqualTo(InvoiceStatus.OPEN);
    assertThat(outboxCount(id)).isZero();
  }

  @Test
  void rejectsPrintingOutsideOpenWithoutChangingStateOrWritingAnEvent() throws Exception {
    Invoice processing = storeInvoice(invoice -> invoice.requestPrint(USER, CHANGED_AT));
    Invoice closed =
        storeInvoice(
            invoice -> {
              invoice.requestPrint(USER, CHANGED_AT);
              invoice.confirmClose(CHANGED_AT.plusSeconds(1));
            });
    Invoice canceled = storeInvoice(invoice -> invoice.cancel("Cancelada", USER, CHANGED_AT));
    for (Invoice invoice : List.of(processing, closed, canceled)) {
      UUID id = invoice.getId();
      print(id, UUID.randomUUID().toString())
          .andExpect(status().isConflict())
          .andExpect(jsonPath("$.type").value("urn:problem-type:invalid-invoice-status"))
          .andExpect(jsonPath("$.currentStatus").value(invoice.getStatus().name()));
      assertThat(load(id).getVersion()).isEqualTo(invoice.getVersion());
      assertThat(outboxCount(id)).isZero();
    }
  }

  @Test
  void rollsBackTheTransitionAndOutboxTogether() {
    Invoice original = storeInvoice(invoice -> {});
    UUID id = original.getId();

    transactions.executeWithoutResult(
        tx -> {
          invoiceUseCases.print(new InvoiceUseCases.Print(id, USER));
          entityManager.flush();
          assertThat(load(id).getStatus()).isEqualTo(InvoiceStatus.PROCESSING);
          assertThat(outboxCount(id)).isEqualTo(1);
          tx.setRollbackOnly();
        });

    Invoice unchanged = load(id);
    assertThat(unchanged.getStatus()).isEqualTo(InvoiceStatus.OPEN);
    assertThat(unchanged.getHistory()).hasSize(1);
    assertThat(unchanged.getPrintRequestId()).isNull();
    assertThat(outboxCount(id)).isZero();
  }

  @Test
  void sendsAResultForAMissingInvoiceToTheDltAndRollsBackTheDedupeMark() throws Exception {
    UUID missing = UUID.randomUUID();
    var result = confirmation(UUID.randomUUID(), missing);
    send(result);

    var deadLetter = receive(CONFIRMED + ".DLT", missing);
    assertThat(json.readTree(deadLetter.value())).isEqualTo(json.readTree(codec.toJson(result)));
    assertThat(processedCount(result.id())).isZero();
  }

  private Invoice storeInvoice(Consumer<Invoice> transition) {
    var items =
        List.of(
            new InvoiceItem.Input(
                PRODUCT,
                "PARAFUSO-M6",
                "Parafuso",
                new Money(new BigDecimal("1.50")),
                new Quantity(3)),
            new InvoiceItem.Input(
                OTHER_PRODUCT,
                "PORCA-M6",
                "Porca",
                new Money(new BigDecimal("0.80")),
                new Quantity(2)));
    return transactions.execute(
        tx -> {
          Invoice invoice = Invoice.create(customer, items, USER, CREATED_AT);
          transition.accept(invoice);
          return invoices.save(invoice);
        });
  }

  private Invoice load(UUID id) {
    return transactions.execute(tx -> invoices.findById(id).orElseThrow());
  }

  private ResultActions print(UUID id, String key) throws Exception {
    return mvc.perform(post(PATH + "/{id}/print", id).header(HEADER, key));
  }

  private long outboxCount(UUID invoiceId) {
    return jdbc.queryForObject(
        "select count(*) from outbox_events where aggregate_id = ?", Long.class, invoiceId);
  }

  private long processedCount(UUID eventId) {
    return jdbc.queryForObject(
        "select count(*) from processed_events where event_id = ? and consumer_group ="
            + " 'billing-service'",
        Long.class,
        eventId);
  }

  private void awaitProcessed(EventEnvelope<?> event) {
    await().atMost(KAFKA_TIMEOUT).until(() -> processedCount(event.id()) == 1);
  }

  private static EventEnvelope<?> confirmation(UUID requestId, UUID invoiceId) {
    return EventEnvelope.of(
        CONFIRMED,
        "stock-service",
        invoiceId.toString(),
        Map.of(
            "printRequestId", requestId,
            "invoiceId", invoiceId,
            "items",
                List.of(
                    Map.of("productId", PRODUCT, "quantity", 3, "balanceAfter", 4),
                    Map.of("productId", OTHER_PRODUCT, "quantity", 2, "balanceAfter", 6))));
  }

  private void send(EventEnvelope<?> event) throws Exception {
    kafkaTemplate
        .send(event.type(), event.subject(), codec.toJson(event))
        .get(10, TimeUnit.SECONDS);
  }

  private ConsumerRecord<String, String> receive(String topic, UUID invoiceId) {
    Map<String, Object> config =
        Map.of(
            ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
            kafka.getBootstrapServers(),
            ConsumerConfig.GROUP_ID_CONFIG,
            "invoice-print-it-" + UUID.randomUUID(),
            ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,
            "earliest",
            ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
            StringDeserializer.class,
            ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
            StringDeserializer.class);
    try (var consumer = new KafkaConsumer<String, String>(config)) {
      consumer.subscribe(List.of(topic));
      return await()
          .pollInSameThread()
          .atMost(KAFKA_TIMEOUT)
          .until(
              () -> {
                for (var record : consumer.poll(Duration.ofMillis(200))) {
                  if (invoiceId.toString().equals(record.key())) {
                    return record;
                  }
                }
                return null;
              },
              Objects::nonNull);
    }
  }

  private record DebitRequested(
      UUID printRequestId,
      UUID invoiceId,
      long invoiceNumber,
      String requestedBy,
      List<RequestedItem> items) {}

  private record RequestedItem(UUID productId, int quantity) {}
}
