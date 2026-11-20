package dev.eliascosta.invoice.billing.adapter.in.rest;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.serverError;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static dev.eliascosta.invoice.billing.adapter.in.rest.InvoiceController.PATH;
import static dev.eliascosta.invoice.common.idempotency.Idempotent.HEADER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import dev.eliascosta.invoice.billing.domain.model.Customer;
import dev.eliascosta.invoice.billing.domain.model.Document;
import dev.eliascosta.invoice.billing.domain.model.Invoice;
import dev.eliascosta.invoice.billing.domain.model.InvoiceItem;
import dev.eliascosta.invoice.billing.domain.model.Money;
import dev.eliascosta.invoice.billing.domain.model.Quantity;
import dev.eliascosta.invoice.billing.domain.port.out.CustomerRepository;
import dev.eliascosta.invoice.billing.domain.port.out.InvoiceRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class InvoiceApiIT {

  private static final String STOCK_BATCH_PATH = "/api/stock/products/batch";
  private static final String USER = "anonymous";
  private static final Instant CREATED_AT = Instant.parse("2026-11-19T10:00:00Z");
  private static final Instant CHANGED_AT = CREATED_AT.plusSeconds(60);
  private static final UUID PRODUCT = UUID.fromString("719c2177-db22-4b8c-acfd-8c4002707d34");
  private static final UUID OTHER_PRODUCT = UUID.fromString("f87c5b58-c21c-44dc-8d73-23c960c97283");

  @Container @ServiceConnection
  static final PostgreSQLContainer postgres =
      new PostgreSQLContainer("postgres:16-alpine").withDatabaseName("billing_db");

  @Container @ServiceConnection
  static final KafkaContainer kafka = new KafkaContainer("apache/kafka-native:4.2.1");

  @RegisterExtension
  static final WireMockExtension stock =
      WireMockExtension.newInstance().options(options().dynamicPort()).build();

  @DynamicPropertySource
  static void stockUrl(DynamicPropertyRegistry properties) {
    properties.add("invoice.stock.base-url", () -> stock.getRuntimeInfo().getHttpBaseUrl());
  }

  @Autowired private MockMvc mvc;
  @Autowired private JsonMapper json;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private CustomerRepository customers;
  @Autowired private InvoiceRepository invoices;
  @Autowired private TransactionTemplate transactions;

  @Test
  void createsAndReplaysAnInvoiceWithSnapshotsAndWithoutCheckingBalance() throws Exception {
    Customer customer = storeCustomer("Cliente original", "52998224725");
    stockProducts(
        product(OTHER_PRODUCT, "OUTRO", "Produto adicional", "2.30", true),
        product(PRODUCT, "ORIGINAL", "Descrição original", "10.50", true));
    String request = createBody(customer.getId(), item(PRODUCT, 5), item(OTHER_PRODUCT, 2));
    UUID key = UUID.randomUUID();

    mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(request))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].field").value(HEADER));

    var created =
        create(key, request)
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("OPEN"))
            .andExpect(jsonPath("$.customer.id").value(customer.getId().toString()))
            .andExpect(jsonPath("$.customer.name").value("Cliente original"))
            .andExpect(jsonPath("$.customer.document").value("52998224725"))
            .andExpect(jsonPath("$.items[0].productId").value(PRODUCT.toString()))
            .andExpect(jsonPath("$.items[0].productCode").value("ORIGINAL"))
            .andExpect(jsonPath("$.items[0].productDescription").value("Descrição original"))
            .andExpect(jsonPath("$.items[0].unitPrice").value(10.5))
            .andExpect(jsonPath("$.items[0].quantity").value(5))
            .andExpect(jsonPath("$.items[0].lineTotal").value(52.5))
            .andExpect(jsonPath("$.items[0].lineOrder").value(1))
            .andExpect(jsonPath("$.items[1].productId").value(OTHER_PRODUCT.toString()))
            .andExpect(jsonPath("$.items[1].lineOrder").value(2))
            .andExpect(jsonPath("$.total").value(57.1))
            .andExpect(jsonPath("$.version").value(0))
            .andExpect(jsonPath("$.allowedActions", contains("EDIT_ITEMS", "CANCEL", "PRINT")))
            .andReturn()
            .getResponse();
    var createdBody = body(created);
    UUID id = UUID.fromString(createdBody.get("id").asString());
    long number = createdBody.get("number").asLong();
    assertThat(number).isPositive();
    assertThat(created.getHeader("Location")).isEqualTo(PATH + "/" + id);

    var replay =
        create(key, request)
            .andExpect(status().isCreated())
            .andExpect(header().string("Location", PATH + "/" + id))
            .andExpect(jsonPath("$.number").value(number))
            .andReturn()
            .getResponse();
    assertThat(body(replay)).isEqualTo(createdBody);
    create(key, createBody(customer.getId(), item(PRODUCT, 6)))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.type").value("urn:problem-type:idempotency-key-payload-mismatch"));

    customer.edit("Cliente atualizado", null, null, USER, CHANGED_AT);
    transactions.executeWithoutResult(tx -> customers.save(customer));
    mvc.perform(get(PATH + "/{id}", id))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.number").value(number))
        .andExpect(jsonPath("$.createdAt").value(createdBody.get("createdAt").asString()))
        .andExpect(jsonPath("$.customer.name").value("Cliente original"))
        .andExpect(jsonPath("$.items[0].productDescription").value("Descrição original"));

    assertThat(invoiceCount(customer.getId())).isEqualTo(1);
    assertThat(
            jdbc.queryForObject("select created_by from invoices where id = ?", String.class, id))
        .isEqualTo(USER);
    assertThat(
            jdbc.queryForObject(
                "select changed_by from invoice_status_history where invoice_id = ?",
                String.class,
                id))
        .isEqualTo(USER);
    stock.verify(1, getRequestedFor(urlPathEqualTo(STOCK_BATCH_PATH)));
    stock.verify(
        1,
        getRequestedFor(urlPathEqualTo(STOCK_BATCH_PATH))
            .withQueryParam("ids", equalTo(PRODUCT + "," + OTHER_PRODUCT)));
  }

  @Test
  void rejectsMissingAndInactiveCustomersBeforeCallingStock() throws Exception {
    UUID missing = UUID.randomUUID();
    create(UUID.randomUUID(), createBody(missing, item(PRODUCT, 1)))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.type").value("urn:problem-type:customer-not-found"))
        .andExpect(jsonPath("$.customerId").value(missing.toString()));

    Customer inactive = storeCustomer("Cliente inativo", "11144477735");
    inactive.deactivate(USER, CHANGED_AT);
    transactions.executeWithoutResult(tx -> customers.save(inactive));
    create(UUID.randomUUID(), createBody(inactive.getId(), item(PRODUCT, 1)))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.type").value("urn:problem-type:customer-inactive"))
        .andExpect(jsonPath("$.customerId").value(inactive.getId().toString()));

    assertThat(invoiceCount(inactive.getId())).isZero();
    stock.verify(0, getRequestedFor(urlPathEqualTo(STOCK_BATCH_PATH)));
  }

  @Test
  void rejectsDuplicateMissingAndInactiveProducts() throws Exception {
    UUID customer = storeCustomer("Cliente para validação", "11222333000181").getId();
    create(UUID.randomUUID(), createBody(customer, item(PRODUCT, 1), item(PRODUCT, 2)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.type").value("urn:problem-type:duplicate-invoice-item"));
    stock.verify(0, getRequestedFor(urlPathEqualTo(STOCK_BATCH_PATH)));

    stockProducts();
    create(UUID.randomUUID(), createBody(customer, item(OTHER_PRODUCT, 1)))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.type").value("urn:problem-type:product-not-found"))
        .andExpect(jsonPath("$.productId").value(OTHER_PRODUCT.toString()));
    stockProducts(product(PRODUCT, "INATIVO", "Produto inativo", "3.20", false));
    create(UUID.randomUUID(), createBody(customer, item(PRODUCT, 1)))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.type").value("urn:problem-type:product-inactive"))
        .andExpect(jsonPath("$.productId").value(PRODUCT.toString()));
    assertThat(invoiceCount(customer)).isZero();
  }

  @Test
  void canRetryWithTheSameKeyAfterStockReturnsAnError() throws Exception {
    UUID customer = storeCustomer("Cliente para nova tentativa", "93541134780").getId();
    String request = createBody(customer, item(PRODUCT, 1));
    UUID key = UUID.randomUUID();
    stock.stubFor(WireMock.get(urlPathEqualTo(STOCK_BATCH_PATH)).willReturn(serverError()));

    create(key, request)
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.type").value("urn:problem-type:stock-service-unavailable"))
        .andExpect(jsonPath("$.detail", containsString("Tente novamente")));
    assertThat(invoiceCount(customer)).isZero();
    assertThat(
            jdbc.queryForObject(
                "select count(*) from idempotency_keys where idempotency_key = ?", Long.class, key))
        .isZero();
    stock.verify(3, getRequestedFor(urlPathEqualTo(STOCK_BATCH_PATH)));

    stockProducts(product(PRODUCT, "ATIVO", "Produto disponível", "3.20", true));
    create(key, request).andExpect(status().isCreated());
    assertThat(invoiceCount(customer)).isEqualTo(1);
    stock.verify(4, getRequestedFor(urlPathEqualTo(STOCK_BATCH_PATH)));
  }

  @Test
  void listsInvoicesWithCombinedFiltersPaginationAndBothPeriodBoundaries() throws Exception {
    Customer customer = storeCustomer("Cliente com notas", "12345678909");
    Customer other = storeCustomer("Outro cliente", "39053344705");
    storeInvoice(customer, CREATED_AT.minusSeconds(1));
    Invoice atStart = storeInvoice(customer, CREATED_AT);
    Invoice within = storeInvoice(customer, CREATED_AT.plusSeconds(1));
    Invoice atEnd = storeInvoice(customer, CHANGED_AT);
    storeInvoice(other, CREATED_AT);
    storeInvoice(customer, CREATED_AT, invoice -> invoice.cancel("Revisão", USER, CHANGED_AT));

    mvc.perform(
            get(PATH)
                .param("status", "OPEN")
                .param("customerId", customer.getId().toString())
                .param("from", CREATED_AT.toString())
                .param("to", CHANGED_AT.toString())
                .param("size", "1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[*].id", contains(within.getId().toString())))
        .andExpect(jsonPath("$.page.number").value(0))
        .andExpect(jsonPath("$.page.size").value(1))
        .andExpect(jsonPath("$.page.totalElements").value(2))
        .andExpect(jsonPath("$.page.totalPages").value(2));
    mvc.perform(
            get(PATH)
                .param("status", "OPEN")
                .param("customerId", customer.getId().toString())
                .param("from", CREATED_AT.toString())
                .param("sort", "createdAt,asc")
                .param("size", "101"))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath(
                "$.content[*].id",
                contains(
                    atStart.getId().toString(),
                    within.getId().toString(),
                    atEnd.getId().toString())))
        .andExpect(jsonPath("$.page.size").value(100));
    mvc.perform(get(PATH).param("from", CHANGED_AT.toString()).param("to", CREATED_AT.toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content").isEmpty())
        .andExpect(jsonPath("$.page.totalElements").value(0));
  }

  @Test
  void replacesItemsWithFreshSnapshotsAndRejectsAStaleVersion() throws Exception {
    Customer customer = storeCustomer("Cliente para edição de nota", "16899535009");
    Invoice original = storeInvoice(customer, CREATED_AT);
    UUID id = original.getId();
    String request =
        """
        {"version":%d,"items":[%s,%s]}
        """
            .formatted(original.getVersion(), item(OTHER_PRODUCT, 1), item(PRODUCT, 4));
    stockProducts(
        product(PRODUCT, "NOVO", "Nova descrição", "4.50", false),
        product(OTHER_PRODUCT, "ADICIONAL", "Outro produto", "1.75", true));

    replace(id, request)
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.type").value("urn:problem-type:product-inactive"));
    mvc.perform(get(PATH + "/{id}", id))
        .andExpect(jsonPath("$.version").value(original.getVersion()))
        .andExpect(jsonPath("$.items[*].productCode", contains("ANTIGO")));

    stockProducts(
        product(PRODUCT, "NOVO", "Nova descrição", "4.50", true),
        product(OTHER_PRODUCT, "ADICIONAL", "Outro produto", "1.75", true));
    var edited =
        replace(id, request)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("OPEN"))
            .andExpect(jsonPath("$.number").value(original.getNumber()))
            .andExpect(jsonPath("$.customer.id").value(customer.getId().toString()))
            .andExpect(jsonPath("$.items[*].productCode", contains("ADICIONAL", "NOVO")))
            .andExpect(jsonPath("$.items[1].productDescription").value("Nova descrição"))
            .andExpect(jsonPath("$.items[1].lineTotal").value(18))
            .andExpect(jsonPath("$.total").value(19.75))
            .andReturn()
            .getResponse();
    long updatedVersion = body(edited).get("version").asLong();
    assertThat(updatedVersion).isGreaterThan(original.getVersion());

    replace(id, request)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.type").value("urn:problem-type:optimistic-lock-conflict"));
    mvc.perform(get(PATH + "/{id}", id))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.version").value(updatedVersion))
        .andExpect(jsonPath("$.items[*].productCode", contains("ADICIONAL", "NOVO")));
    assertThat(
            jdbc.queryForObject("select updated_by from invoices where id = ?", String.class, id))
        .isEqualTo(USER);
    stock.verify(2, getRequestedFor(urlPathEqualTo(STOCK_BATCH_PATH)));
  }

  @Test
  void cancelsAnOpenInvoiceAndRecordsTheReasonWithoutCallingStock() throws Exception {
    Customer customer = storeCustomer("Cliente para cancelamento", "04252011000110");
    Invoice invoice = storeInvoice(customer, CREATED_AT);

    cancel(invoice.getId())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("CANCELED"))
        .andExpect(jsonPath("$.canceledAt").isNotEmpty())
        .andExpect(jsonPath("$.cancelReason").value("Pedido cancelado"))
        .andExpect(jsonPath("$.allowedActions").isEmpty());
    mvc.perform(get(PATH + "/{id}", invoice.getId()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("CANCELED"))
        .andExpect(jsonPath("$.cancelReason").value("Pedido cancelado"));
    assertThat(
            jdbc.queryForObject(
                "select changed_by from invoice_status_history where invoice_id = ? and to_status ="
                    + " 'CANCELED'",
                String.class,
                invoice.getId()))
        .isEqualTo(USER);
    stock.verify(0, getRequestedFor(urlPathEqualTo(STOCK_BATCH_PATH)));
  }

  @Test
  void rejectsEditsAndCancellationForAllNonOpenStatuses() throws Exception {
    Customer customer = storeCustomer("Cliente com notas encerradas", "11444777000161");
    Invoice processing = storeInvoice(customer, CREATED_AT, i -> i.requestPrint(USER, CHANGED_AT));
    Invoice closed =
        storeInvoice(
            customer,
            CREATED_AT,
            i -> {
              i.requestPrint(USER, CHANGED_AT);
              i.confirmClose(CHANGED_AT.plusSeconds(1));
            });
    Invoice canceled =
        storeInvoice(customer, CREATED_AT, i -> i.cancel("Revisão", USER, CHANGED_AT));
    Invoice reopened =
        storeInvoice(
            customer,
            CREATED_AT,
            i -> {
              i.requestPrint(USER, CHANGED_AT);
              i.rejectClose("Saldo insuficiente", CHANGED_AT.plusSeconds(1));
            });

    for (Invoice invoice : List.of(processing, closed, canceled)) {
      String request =
          """
          {"version":%d,"items":[%s]}
          """
              .formatted(invoice.getVersion(), item(PRODUCT, 2));
      replace(invoice.getId(), request)
          .andExpect(status().isConflict())
          .andExpect(jsonPath("$.type").value("urn:problem-type:invalid-invoice-status"))
          .andExpect(jsonPath("$.currentStatus").value(invoice.getStatus().name()))
          .andExpect(jsonPath("$.allowedTransitions").isArray());
      cancel(invoice.getId())
          .andExpect(status().isConflict())
          .andExpect(jsonPath("$.type").value("urn:problem-type:invalid-invoice-status"));
      mvc.perform(get(PATH + "/{id}", invoice.getId()))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.allowedActions").isEmpty());
    }
    mvc.perform(get(PATH + "/{id}", reopened.getId()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.lastFailureReason").value("Saldo insuficiente"))
        .andExpect(jsonPath("$.allowedActions", contains("EDIT_ITEMS", "CANCEL", "PRINT")));
    stock.verify(0, getRequestedFor(urlPathEqualTo(STOCK_BATCH_PATH)));
  }

  @Test
  void returnsNotFoundWhenAnIndividualRouteTargetsAMissingInvoice() throws Exception {
    UUID missing = UUID.randomUUID();
    mvc.perform(get(PATH + "/{id}", missing))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.type").value("urn:problem-type:invoice-not-found"));
    replace(missing, "{\"version\":0,\"items\":[" + item(PRODUCT, 1) + "]}")
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.type").value("urn:problem-type:invoice-not-found"));
    cancel(missing)
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.type").value("urn:problem-type:invoice-not-found"));
  }

  private ResultActions create(UUID key, String request) throws Exception {
    return mvc.perform(
        post(PATH)
            .header(HEADER, key.toString())
            .contentType(MediaType.APPLICATION_JSON)
            .content(request));
  }

  private ResultActions replace(UUID id, String request) throws Exception {
    return mvc.perform(
        put(PATH + "/{id}/items", id).contentType(MediaType.APPLICATION_JSON).content(request));
  }

  private ResultActions cancel(UUID id) throws Exception {
    return mvc.perform(
        post(PATH + "/{id}/cancel", id)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"reason\":\"Pedido cancelado\"}"));
  }

  private JsonNode body(MockHttpServletResponse response) {
    return json.readTree(response.getContentAsByteArray());
  }

  private long invoiceCount(UUID customerId) {
    return jdbc.queryForObject(
        "select count(*) from invoices where customer_id = ?", Long.class, customerId);
  }

  private Customer storeCustomer(String name, String document) {
    return transactions.execute(
        tx ->
            customers.save(
                Customer.create(name, new Document(document), null, null, USER, CREATED_AT)));
  }

  private Invoice storeInvoice(Customer customer, Instant at) {
    return storeInvoice(customer, at, invoice -> {});
  }

  private Invoice storeInvoice(Customer customer, Instant at, Consumer<Invoice> transition) {
    return transactions.execute(
        tx -> {
          var snapshot =
              new InvoiceItem.Input(
                  PRODUCT,
                  "ANTIGO",
                  "Descrição antiga",
                  new Money(new BigDecimal("2.50")),
                  new Quantity(1));
          Invoice invoice = Invoice.create(customer, List.of(snapshot), USER, at);
          transition.accept(invoice);
          return invoices.save(invoice);
        });
  }

  private void stockProducts(String... products) {
    stock.stubFor(
        WireMock.get(urlPathEqualTo(STOCK_BATCH_PATH))
            .willReturn(okJson("[" + String.join(",", products) + "]")));
  }

  private static String product(
      UUID id, String code, String description, String price, boolean active) {
    return """
    {"id":"%s","code":"%s","description":"%s","unitPrice":%s,"balance":0,"active":%s}
    """
        .formatted(id, code, description, price, active);
  }

  private static String createBody(UUID customerId, String... items) {
    return """
    {"customerId":"%s","items":[%s]}
    """
        .formatted(customerId, String.join(",", items));
  }

  private static String item(UUID productId, int quantity) {
    return """
    {"productId":"%s","quantity":%d}
    """
        .formatted(productId, quantity);
  }
}
