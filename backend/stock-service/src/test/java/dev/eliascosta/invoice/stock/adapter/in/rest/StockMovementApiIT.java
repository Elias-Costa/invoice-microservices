package dev.eliascosta.invoice.stock.adapter.in.rest;

import static dev.eliascosta.invoice.common.idempotency.Idempotent.HEADER;
import static dev.eliascosta.invoice.stock.adapter.in.rest.ProductController.PATH;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
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
class StockMovementApiIT {

  private static final String PRODUCT_PATH = PATH + "/{id}";
  private static final String MOVEMENTS_PATH = PRODUCT_PATH + "/movements";

  @Container @ServiceConnection
  static final PostgreSQLContainer postgres =
      new PostgreSQLContainer("postgres:16-alpine").withDatabaseName("stock_db");

  @Container @ServiceConnection
  static final KafkaContainer kafka = new KafkaContainer("apache/kafka-native:4.2.1");

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private JsonMapper json;

  @Test
  void registersAndReplaysAnInboundAndShowsBothManualMovementsInNewestFirstOrder()
      throws Exception {
    UUID id = createProduct("F110-MOVEMENTS", 5);
    String key = UUID.randomUUID().toString();
    String inbound = movementJson("INBOUND", 5);
    JsonNode created =
        body(
            register(id, key, inbound)
                .andExpect(status().isCreated())
                .andExpect(header().doesNotExist(HttpHeaders.LOCATION))
                .andExpect(jsonPath("$.productId").value(id.toString()))
                .andExpect(jsonPath("$.type").value("INBOUND"))
                .andExpect(jsonPath("$.quantity").value(5))
                .andExpect(jsonPath("$.balanceAfter").value(10))
                .andExpect(jsonPath("$.reason").value("Ajuste de estoque"))
                .andExpect(jsonPath("$.createdBy").value("anonymous")));
    assertThat(created.path("referenceId").isNull()).isTrue();
    assertThat(created.path("referenceNumber").isNull()).isTrue();
    Instant createdAt = Instant.parse(created.path("createdAt").asString());

    JsonNode outbound =
        body(
            register(id, movementJson("OUTBOUND", 3))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("OUTBOUND"))
                .andExpect(jsonPath("$.balanceAfter").value(7)));
    assertThat(Instant.parse(outbound.path("createdAt").asString())).isAfter(createdAt);

    JsonNode replay = body(register(id, key, inbound).andExpect(status().isCreated()));
    assertThat(replay).isEqualTo(created);
    register(id, key, movementJson("INBOUND", 4))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.type").value("urn:problem-type:idempotency-key-payload-mismatch"));
    mvc.perform(get(PRODUCT_PATH, id))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.balance").value(7))
        .andExpect(jsonPath("$.version").value(2));

    JsonNode firstPage =
        body(
            mvc.perform(get(MOVEMENTS_PATH, id).param("page", "0").param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.number").value(0))
                .andExpect(jsonPath("$.page.size").value(1))
                .andExpect(jsonPath("$.page.totalElements").value(3))
                .andExpect(jsonPath("$.page.totalPages").value(3)));
    assertThat(firstPage.path("content")).containsExactly(outbound);

    JsonNode secondPage =
        body(
            mvc.perform(get(MOVEMENTS_PATH, id).param("page", "1").param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.number").value(1)));
    assertThat(secondPage.path("content")).containsExactly(created);

    mvc.perform(get(MOVEMENTS_PATH, id).param("page", "0").param("size", "20"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[2].type").value("INITIAL"))
        .andExpect(jsonPath("$.content[2].balanceAfter").value(5));
  }

  @Test
  void rejectsAnOutboundExceedingTheBalance() throws Exception {
    UUID id = createProduct("F110-INSUFFICIENT", 3);

    register(id, movementJson("OUTBOUND", 4))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.type").value("urn:problem-type:insufficient-stock"));
    assertUnchangedStock(id, 3);
  }

  @Test
  void allowsInboundAndHistoryButRejectsOutboundWhileInactive() throws Exception {
    UUID id = createProduct("F110-INACTIVE", 2);
    mvc.perform(delete(PRODUCT_PATH, id)).andExpect(status().isNoContent());

    register(id, movementJson("INBOUND", 4))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.balanceAfter").value(6));
    register(id, movementJson("OUTBOUND", 1))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.type").value("urn:problem-type:product-inactive"));
    mvc.perform(get(PRODUCT_PATH, id))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.active").value(false))
        .andExpect(jsonPath("$.balance").value(6));
    mvc.perform(get(MOVEMENTS_PATH, id))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.page.size").value(20))
        .andExpect(jsonPath("$.page.totalElements").value(2));
  }

  @Test
  void rejectsBalanceOverflowWithoutAppendingToHistory() throws Exception {
    UUID id = createProduct("F110-OVERFLOW", Integer.MAX_VALUE);

    register(id, movementJson("INBOUND", 1))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.type").value("urn:problem-type:validation-error"));
    assertUnchangedStock(id, Integer.MAX_VALUE);
  }

  @Test
  void requiresAUuidIdempotencyKeyBeforeChangingStock() throws Exception {
    UUID id = createProduct("F110-KEY", 1);
    String inbound = movementJson("INBOUND", 2);

    mvc.perform(post(MOVEMENTS_PATH, id).contentType(MediaType.APPLICATION_JSON).content(inbound))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].field").value(HEADER));
    register(id, "not-a-uuid", inbound)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].field").value(HEADER));
    assertUnchangedStock(id, 1);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "{\"type\":\"INITIAL\",\"quantity\":1,\"reason\":\"Ajuste\"}",
        "{\"type\":\"INVOICE_DEBIT\",\"quantity\":1,\"reason\":\"Ajuste\"}",
        "{\"type\":\"INBOUND\",\"quantity\":0,\"reason\":\"Ajuste\"}",
        "{\"type\":\"INBOUND\",\"quantity\":1.5,\"reason\":\"Ajuste\"}",
        "{\"type\":\"INBOUND\",\"quantity\":1,\"reason\":\"  \"}",
        "{\"type\":\"OUTBOUND\",\"quantity\":1}"
      })
  void rejectsAnInvalidManualMovement(String request) throws Exception {
    UUID id = createProduct("F110-INVALID-" + UUID.randomUUID().toString().substring(0, 8), 2);

    register(id, request)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.type").value("urn:problem-type:validation-error"));
    assertUnchangedStock(id, 2);
  }

  @Test
  void returnsNotFoundForAnUnknownProductInBothRoutes() throws Exception {
    UUID id = UUID.randomUUID();
    register(id, movementJson("INBOUND", 1))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.type").value("urn:problem-type:product-not-found"));
    mvc.perform(get(MOVEMENTS_PATH, id))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.type").value("urn:problem-type:product-not-found"));
  }

  @Test
  void returnsReferenceFieldsFromStoredInvoiceMovements() throws Exception {
    UUID id = createProduct("F110-INVOICE-REFERENCE", 5);
    UUID referenceId = UUID.randomUUID();
    jdbc.update(
        """
        INSERT INTO stock_movements
          (id, product_id, type, quantity, balance_after, reference_id, reference_number,
           created_by, created_at)
        SELECT ?, id, 'INVOICE_DEBIT', 2, 3, ?, 42, 'operator', created_at + interval '1 second'
        FROM products
        WHERE id = ?
        """,
        UUID.randomUUID(),
        referenceId,
        id);

    mvc.perform(get(MOVEMENTS_PATH, id).param("size", "101"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.page.size").value(100))
        .andExpect(jsonPath("$.content[0].type").value("INVOICE_DEBIT"))
        .andExpect(jsonPath("$.content[0].referenceId").value(referenceId.toString()))
        .andExpect(jsonPath("$.content[0].referenceNumber").value(42))
        .andExpect(jsonPath("$.content[0].createdBy").value("operator"));
    mvc.perform(get(MOVEMENTS_PATH, id).param("page", "-1").param("size", "0"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.type").value("urn:problem-type:validation-error"));
  }

  private UUID createProduct(String code, int balance) throws Exception {
    String request =
        """
        {"code":"%s","description":"Parafuso","unitPrice":2.30,"initialBalance":%d}
        """
            .formatted(code, balance);
    return UUID.fromString(
        body(mvc.perform(
                    post(PATH)
                        .header(HEADER, UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isCreated()))
            .path("id")
            .asString());
  }

  private void assertUnchangedStock(UUID id, int balance) throws Exception {
    mvc.perform(get(PRODUCT_PATH, id))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.balance").value(balance))
        .andExpect(jsonPath("$.version").value(0));
    mvc.perform(get(MOVEMENTS_PATH, id))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.page.totalElements").value(1));
  }

  private ResultActions register(UUID id, String request) throws Exception {
    return register(id, UUID.randomUUID().toString(), request);
  }

  private ResultActions register(UUID id, String key, String request) throws Exception {
    return mvc.perform(
        post(MOVEMENTS_PATH, id)
            .header(HEADER, key)
            .contentType(MediaType.APPLICATION_JSON)
            .content(request));
  }

  private JsonNode body(ResultActions response) throws Exception {
    return json.readTree(response.andReturn().getResponse().getContentAsByteArray());
  }

  private static String movementJson(String type, int quantity) {
    return """
    {"type":"%s","quantity":%d,"reason":"Ajuste de estoque"}
    """
        .formatted(type, quantity);
  }
}
