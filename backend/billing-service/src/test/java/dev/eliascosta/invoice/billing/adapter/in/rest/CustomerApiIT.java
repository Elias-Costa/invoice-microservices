package dev.eliascosta.invoice.billing.adapter.in.rest;

import static dev.eliascosta.invoice.billing.adapter.in.rest.CustomerController.PATH;
import static dev.eliascosta.invoice.common.idempotency.Idempotent.HEADER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.eliascosta.invoice.billing.domain.model.Customer;
import dev.eliascosta.invoice.billing.domain.model.Invoice;
import dev.eliascosta.invoice.billing.domain.model.InvoiceItem;
import dev.eliascosta.invoice.billing.domain.model.InvoiceStatus;
import dev.eliascosta.invoice.billing.domain.model.Money;
import dev.eliascosta.invoice.billing.domain.model.Quantity;
import dev.eliascosta.invoice.billing.domain.port.out.CustomerRepository;
import dev.eliascosta.invoice.billing.domain.port.out.InvoiceRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ActiveProfiles;
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
class CustomerApiIT {

  @Container @ServiceConnection
  static final PostgreSQLContainer postgres =
      new PostgreSQLContainer("postgres:16-alpine").withDatabaseName("billing_db");

  @Container @ServiceConnection
  static final KafkaContainer kafka = new KafkaContainer("apache/kafka-native:4.2.1");

  @Autowired private MockMvc mvc;
  @Autowired private JsonMapper json;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private CustomerRepository customers;
  @Autowired private InvoiceRepository invoices;
  @Autowired private TransactionTemplate transactions;

  @Test
  void createsCustomerOnceAndReturnsTheSpecificConflictForADifferentKey() throws Exception {
    String document = "52998224725";
    String request = customerJson("Cliente Idempotente", document);
    String key = UUID.randomUUID().toString();

    mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(request))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].field").value(HEADER));
    create("not-a-uuid", request)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].field").value(HEADER));

    var created =
        create(key, request)
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.documentType").value("CPF"))
            .andExpect(jsonPath("$.version").value(0))
            .andReturn()
            .getResponse();
    UUID id = idFrom(created);

    assertThat(created.getHeader("Location")).isEqualTo(PATH + "/" + id);

    var replay =
        create(
                key,
                """
                {"document":"52998224725","name":"Cliente Idempotente"}
                """)
            .andExpect(status().isCreated())
            .andExpect(header().string("Location", PATH + "/" + id))
            .andReturn()
            .getResponse();
    assertThat(body(replay)).isEqualTo(body(created));

    create(key, customerJson("Nome diferente", document))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.type").value("urn:problem-type:idempotency-key-payload-mismatch"));
    create(UUID.randomUUID().toString(), request)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.type").value("urn:problem-type:customer-document-already-exists"));

    assertThat(
            jdbc.queryForList(
                "select created_by from customers where document = ?", String.class, document))
        .containsExactly("anonymous");
  }

  @ParameterizedTest
  @ValueSource(strings = {"52998224726", "11222333000182"})
  void rejectsInvalidCpfOrCnpjCheckDigits(String document) throws Exception {
    create(UUID.randomUUID().toString(), customerJson("Documento inválido", document))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.type").value("urn:problem-type:validation-error"))
        .andExpect(jsonPath("$.errors[0].field").value("document"));
  }

  @Test
  void editsContactsAndRejectsDocumentChangesAndStaleVersions() throws Exception {
    String document = "11144477735";
    UUID id = createCustomer("Cliente para edição", document);

    update(
            id,
            """
            {"name":"Nome alterado","document":"93541134780","version":0}
            """)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.type").value("urn:problem-type:field-immutable"))
        .andExpect(jsonPath("$.errors[0].field").value("document"));

    String update =
        """
        {"name":"Nome alterado","document":"11144477735","email":"novo@example.com","phone":"5585999990000","version":0}
        """;
    update(id, update)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.document").value(document))
        .andExpect(jsonPath("$.email").value("novo@example.com"))
        .andExpect(jsonPath("$.phone").value("5585999990000"))
        .andExpect(jsonPath("$.version").value(1));

    update(id, update)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.type").value("urn:problem-type:optimistic-lock-conflict"));

    update(
            id,
            """
            {"name":"Nome alterado","version":1}
            """)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.document").value(document))
        .andExpect(jsonPath("$.email").value(nullValue()))
        .andExpect(jsonPath("$.phone").value(nullValue()))
        .andExpect(jsonPath("$.version").value(2));

    assertThat(
            jdbc.queryForObject("select updated_by from customers where id = ?", String.class, id))
        .isEqualTo("anonymous");
  }

  @Test
  void listsWithNameSearchDocumentPrefixPaginationAndActiveFilter() throws Exception {
    UUID beta = createCustomer("Beta Pesquisa", "04252011000110");
    UUID alpha = createCustomer("Alfa Pesquisa", "11222333000181");
    UUID gamma = createCustomer("Gama Pesquisa", "93541134780");

    mvc.perform(delete(PATH + "/{id}", gamma)).andExpect(status().isNoContent());
    mvc.perform(get(PATH + "/{id}", gamma))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.active").value(false))
        .andExpect(jsonPath("$.recentInvoices").isEmpty());

    mvc.perform(get(PATH).param("search", "PESQUI").param("size", "1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[*].id", contains(alpha.toString())))
        .andExpect(jsonPath("$.page.totalElements").value(2))
        .andExpect(jsonPath("$.page.totalPages").value(2));
    mvc.perform(get(PATH).param("search", "PESQUI").param("page", "1").param("size", "1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[*].id", contains(beta.toString())))
        .andExpect(jsonPath("$.page.number").value(1));

    mvc.perform(
            get(PATH).param("search", "pesqui").param("size", "101").param("sort", "document,asc"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[*].id", contains(beta.toString(), alpha.toString())))
        .andExpect(jsonPath("$.page.size").value(100));
    mvc.perform(get(PATH).param("search", "042520"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[*].id", contains(beta.toString())))
        .andExpect(jsonPath("$.content[0].documentType").value("CNPJ"));
    mvc.perform(get(PATH).param("search", "252011"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content").isEmpty());
    mvc.perform(get(PATH).param("search", "0425%"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content").isEmpty());

    mvc.perform(get(PATH).param("search", "pesqui").param("active", "false"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[*].id", contains(gamma.toString())));
    mvc.perform(post(PATH + "/{id}/reactivate", gamma))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.active").value(true))
        .andExpect(jsonPath("$.version").value(2));
    mvc.perform(get(PATH).param("search", "pesqui").param("active", "false"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content").isEmpty());
  }

  @Test
  void detailReturnsOnlyTheFiveLatestInvoicesOfThatCustomerInCreationOrder() throws Exception {
    UUID id = createCustomer("Cliente com notas", "12345678909");
    UUID otherId = createCustomer("Outro cliente com nota", "11444777000161");
    Instant origin = Instant.parse("2026-11-19T12:00:00Z");

    List<Invoice> recent =
        transactions.execute(
            transaction -> {
              Customer customer = customers.findById(id).orElseThrow();
              Customer other = customers.findById(otherId).orElseThrow();
              Invoice middle = saveInvoice(customer, origin.plusSeconds(3), InvoiceStatus.OPEN);
              Invoice latest =
                  saveInvoice(customer, origin.plusSeconds(6), InvoiceStatus.PROCESSING);
              saveInvoice(customer, origin, InvoiceStatus.OPEN);
              Invoice canceled =
                  saveInvoice(customer, origin.plusSeconds(5), InvoiceStatus.CANCELED);
              Invoice oldestInRange =
                  saveInvoice(customer, origin.plusSeconds(2), InvoiceStatus.OPEN);
              saveInvoice(customer, origin.plusSeconds(1), InvoiceStatus.OPEN);
              Invoice closed = saveInvoice(customer, origin.plusSeconds(4), InvoiceStatus.CLOSED);
              saveInvoice(other, origin.plusSeconds(7), InvoiceStatus.OPEN);
              return List.of(latest, canceled, closed, middle, oldestInRange);
            });

    String[] expectedIds =
        recent.stream().map(invoice -> invoice.getId().toString()).toArray(String[]::new);
    mvc.perform(get(PATH + "/{id}", id))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(id.toString()))
        .andExpect(jsonPath("$.recentInvoices[*].id", contains(expectedIds)))
        .andExpect(
            jsonPath(
                "$.recentInvoices[*].status",
                contains("PROCESSING", "CANCELED", "CLOSED", "OPEN", "OPEN")))
        .andExpect(
            jsonPath("$.recentInvoices[0].number").value(recent.getFirst().getNumber().intValue()))
        .andExpect(jsonPath("$.recentInvoices[0].total").value(12.50))
        .andExpect(
            jsonPath("$.recentInvoices[0].createdAt").value(origin.plusSeconds(6).toString()))
        .andExpect(jsonPath("$.recentInvoices[0].customerName").doesNotExist());
  }

  private Invoice saveInvoice(Customer customer, Instant createdAt, InvoiceStatus status) {
    var item =
        new InvoiceItem.Input(
            UUID.randomUUID(),
            "ITEM",
            "Produto",
            new Money(new BigDecimal("12.50")),
            new Quantity(1));
    Invoice invoice = Invoice.create(customer, List.of(item), "test", createdAt);
    Instant transitionAt = createdAt.plusSeconds(1);
    switch (status) {
      case OPEN -> {}
      case PROCESSING -> invoice.requestPrint("test", transitionAt);
      case CANCELED -> invoice.cancel("Cancelamento de teste", "test", transitionAt);
      case CLOSED -> {
        invoice.requestPrint("test", transitionAt);
        invoice.confirmClose(transitionAt.plusSeconds(1));
      }
    }
    return invoices.save(invoice);
  }

  private UUID createCustomer(String name, String document) throws Exception {
    return idFrom(
        create(UUID.randomUUID().toString(), customerJson(name, document))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse());
  }

  private ResultActions create(String key, String request) throws Exception {
    return mvc.perform(
        post(PATH).header(HEADER, key).contentType(MediaType.APPLICATION_JSON).content(request));
  }

  private ResultActions update(UUID id, String request) throws Exception {
    return mvc.perform(
        put(PATH + "/{id}", id).contentType(MediaType.APPLICATION_JSON).content(request));
  }

  private UUID idFrom(MockHttpServletResponse response) throws Exception {
    return UUID.fromString(body(response).get("id").asString());
  }

  private JsonNode body(MockHttpServletResponse response) throws Exception {
    return json.readTree(response.getContentAsByteArray());
  }

  private static String customerJson(String name, String document) {
    return """
    {"name":"%s","document":"%s"}
    """
        .formatted(name, document);
  }
}
