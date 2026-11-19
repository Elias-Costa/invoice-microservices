package dev.eliascosta.invoice.stock.adapter.in.rest;

import static dev.eliascosta.invoice.common.idempotency.Idempotent.HEADER;
import static dev.eliascosta.invoice.stock.adapter.in.rest.ProductController.PATH;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
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
class ProductApiIT {

  @Container @ServiceConnection
  static final PostgreSQLContainer postgres =
      new PostgreSQLContainer("postgres:16-alpine").withDatabaseName("stock_db");

  @Container @ServiceConnection
  static final KafkaContainer kafka = new KafkaContainer("apache/kafka-native:4.2.1");

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private JsonMapper json;

  @Test
  void createsAndReplaysTheSameProductAndLocationWithExactlyOneInitialMovement() throws Exception {
    String code = "REPLAY-F109";
    String key = UUID.randomUUID().toString();
    var created =
        create(key, productJson(" replay-f109 ", "Parafuso", 5))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.code").value(code))
            .andExpect(jsonPath("$.balance").value(5))
            .andReturn()
            .getResponse();
    UUID id = idFrom(created);
    String location = PATH + "/" + id;

    assertThat(created.getHeader(HttpHeaders.LOCATION)).isEqualTo(location);
    var replay =
        create(
                key,
                """
                {"initialBalance":5,"unitPrice":2.30,"description":"Parafuso","code":" replay-f109 "}
                """)
            .andExpect(status().isCreated())
            .andExpect(header().string(HttpHeaders.LOCATION, location))
            .andReturn()
            .getResponse();

    assertThat(body(replay)).isEqualTo(body(created));
    mvc.perform(get(location)).andExpect(status().isOk()).andExpect(jsonPath("$.balance").value(5));
    assertThat(
            jdbc.queryForObject("SELECT count(*) FROM products WHERE code = ?", Long.class, code))
        .isEqualTo(1L);
    assertThat(
            jdbc.queryForObject("SELECT created_by FROM products WHERE id = ?", String.class, id))
        .isEqualTo("anonymous");
    assertThat(
            jdbc.queryForList(
                """
                SELECT type, quantity, balance_after, created_by
                FROM stock_movements
                WHERE product_id = ?
                """,
                id))
        .singleElement()
        .satisfies(
            movement ->
                assertThat(movement)
                    .containsEntry("type", "INITIAL")
                    .containsEntry("quantity", 5)
                    .containsEntry("balance_after", 5)
                    .containsEntry("created_by", "anonymous"));

    create(key, productJson(code, "Outro corpo", 5))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.type").value("urn:problem-type:idempotency-key-payload-mismatch"));
  }

  @Test
  void requiresAUuidKeyAndPreservesTheSpecificNormalizedCodeConflict() throws Exception {
    String product = productJson("DUP-F109", "Duplicado", 0);

    mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(product))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].field").value(HEADER));
    create("invalid", product)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].field").value(HEADER));
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM products WHERE code = 'DUP-F109'", Long.class))
        .isZero();

    // The shared contract accepts any well-formed UUID, including versions other than v4.
    UUID id =
        idFrom(
            create("00000000-0000-1000-8000-000000000001", product)
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse());
    assertThat(
            jdbc.queryForObject(
                "SELECT quantity FROM stock_movements WHERE product_id = ? AND type = 'INITIAL'",
                Integer.class,
                id))
        .isZero();

    create(UUID.randomUUID().toString(), productJson(" dup-f109 ", "Outro produto", 0))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.type").value("urn:problem-type:product-code-already-exists"));
  }

  @Test
  void filtersSortsAndPaginatesProductsAndReturnsInactiveProductsInTheBatchShape()
      throws Exception {
    UUID lower = createProduct("F109-LOWER", "Arruela teste-busca", 2);
    UUID higher = createProduct("TESTE-BUSCA-F109", "Porca com trava", 8);
    UUID inactive = createProduct("F109-INACTIVE", "Parafuso teste-busca", 10);
    mvc.perform(delete(PATH + "/{id}", inactive)).andExpect(status().isNoContent());
    mvc.perform(delete(PATH + "/{id}", inactive)).andExpect(status().isNoContent());

    mvc.perform(
            get(PATH)
                .param("search", "TeStE-BuScA")
                .param("size", "1")
                .param("sort", "balance,desc"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].id").value(higher.toString()))
        .andExpect(jsonPath("$.page.totalElements").value(2))
        .andExpect(jsonPath("$.page.totalPages").value(2));
    mvc.perform(
            get(PATH)
                .param("search", "TeStE-BuScA")
                .param("size", "1")
                .param("page", "1")
                .param("sort", "balance,desc"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].id").value(lower.toString()));

    mvc.perform(
            get(PATH).param("search", "teste-busca").param("active", "false").param("size", "101"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].id").value(inactive.toString()))
        .andExpect(jsonPath("$.page.size").value(100))
        .andExpect(jsonPath("$.page.totalElements").value(1));
    mvc.perform(get(PATH + "/{id}", inactive))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.active").value(false));
    mvc.perform(get(PATH + "/batch").param("ids", inactive + "," + UUID.randomUUID()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].id").value(inactive.toString()))
        .andExpect(jsonPath("$[0].active").value(false))
        .andExpect(jsonPath("$[0].version").doesNotExist());

    mvc.perform(post(PATH + "/{id}/reactivate", inactive))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.active").value(true))
        .andExpect(jsonPath("$.version").value(2));
    mvc.perform(post(PATH + "/{id}/reactivate", inactive))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.version").value(2));
    mvc.perform(get(PATH).param("search", "teste-busca"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.page.totalElements").value(3));
  }

  @Test
  void validatesImmutableFieldsAndOptimisticVersionBeforeUpdatingDescriptionAndPrice()
      throws Exception {
    UUID id = createProduct("EDIT-F109", "Editar", 6);
    mvc.perform(
            put(PATH + "/{id}", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"code":"OUTRO","description":"Primeira edição","unitPrice":3.54,"version":0}
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.type").value("urn:problem-type:field-immutable"));
    mvc.perform(
            put(PATH + "/{id}", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"code":" edit-f109 ","description":"Primeira edição","unitPrice":3.546,"version":0}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.balance").value(6))
        .andExpect(jsonPath("$.unitPrice").value(3.55))
        .andExpect(jsonPath("$.version").value(1));
    mvc.perform(
            put(PATH + "/{id}", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"description":"Edição concorrente","unitPrice":9.99,"version":0}
                    """))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.type").value("urn:problem-type:optimistic-lock-conflict"));
    mvc.perform(get(PATH + "/{id}", id))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.description").value("Primeira edição"));
    assertThat(
            jdbc.queryForObject("SELECT updated_by FROM products WHERE id = ?", String.class, id))
        .isEqualTo("anonymous");
  }

  private UUID createProduct(String code, String description, int balance) throws Exception {
    return idFrom(
        create(UUID.randomUUID().toString(), productJson(code, description, balance))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse());
  }

  private ResultActions create(String key, String request) throws Exception {
    return mvc.perform(
        post(PATH).header(HEADER, key).contentType(MediaType.APPLICATION_JSON).content(request));
  }

  private UUID idFrom(MockHttpServletResponse response) throws Exception {
    return UUID.fromString(body(response).path("id").asString());
  }

  private JsonNode body(MockHttpServletResponse response) throws Exception {
    return json.readTree(response.getContentAsByteArray());
  }

  private static String productJson(String code, String description, int balance) {
    return """
    {"code":"%s","description":"%s","unitPrice":2.30,"initialBalance":%d}
    """
        .formatted(code, description, balance);
  }
}
