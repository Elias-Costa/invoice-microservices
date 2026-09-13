package dev.eliascosta.invoice.common.idempotency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.eliascosta.invoice.sampleservice.IdempotencyTestController;
import dev.eliascosta.invoice.sampleservice.SampleServiceApplication;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;
import org.springframework.scheduling.config.CronTask;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * End-to-end contract of HTTP idempotency (RN-16, requisitos.md §8.9 / §6.7, modelo-de-dados.md
 * §6.3 / §12, D-012, D-013) against a real PostgreSQL with the reference DDL, driven through the
 * filter, the interceptor and the shared error handling by MockMvc.
 *
 * <p>The application under test is {@link SampleServiceApplication}, deliberately outside {@code
 * dev.eliascosta.invoice.common}: it proves a service gets the entity, the repository, the filter
 * and the interceptor from the auto-configuration alone.
 *
 * <p>Replayed bodies are compared as JSON trees, not as text: {@code response_body} is {@code
 * jsonb}, which normalizes whitespace and key order (D-014).
 */
@SpringBootTest(
    classes = SampleServiceApplication.class,
    properties = {
      "spring.jpa.hibernate.ddl-auto=validate",
      "spring.jpa.open-in-view=false",
      "spring.jpa.properties.hibernate.jdbc.time_zone=UTC",
      "invoice.idempotency.cleanup.batch-size=2",
      "invoice.idempotency.cleanup.cron=0 30 3 * * *"
    })
@AutoConfigureMockMvc
@Testcontainers
@ExtendWith(OutputCaptureExtension.class)
class IdempotencyIT {

  @Container @ServiceConnection
  static final PostgreSQLContainer postgres =
      new PostgreSQLContainer("postgres:16-alpine").withInitScript("db/technical_tables.sql");

  private static final String URN = "urn:problem-type:";
  private static final String BODY = "{\"name\":\"Parafuso M6\",\"quantity\":3}";
  private static final String ANONYMOUS = IdempotencyUserResolver.ANONYMOUS;

  @Autowired private MockMvc mockMvc;
  @Autowired private IdempotencyKeyRepository repository;
  @Autowired private IdempotencyKeyStore store;
  @Autowired private IdempotencyKeyCleaner cleaner;
  @Autowired private IdempotencyProperties properties;
  @Autowired private IdempotencyTestController controller;
  @Autowired private Clock clock;

  @BeforeEach
  void cleanSlate() {
    repository.deleteAll();
    controller.reset();
  }

  private static final JsonMapper JSON = JsonMapper.builder().build();

  private static String newKey() {
    return UUID.randomUUID().toString();
  }

  /** Structural view of a JSON body — the replay is equivalent JSON, not identical bytes. */
  private static JsonNode json(String body) {
    return JSON.readTree(body);
  }

  private static MockHttpServletRequestBuilder post(
      String path, @Nullable String key, @Nullable String body) {
    MockHttpServletRequestBuilder request =
        MockMvcRequestBuilders.post(path).contentType(MediaType.APPLICATION_JSON);
    if (key != null) {
      request.header(Idempotent.HEADER, key);
    }
    if (body != null) {
      request.content(body);
    }
    return request;
  }

  private IdempotencyKeyEntity row(String key, String user) {
    return repository
        .findById(new IdempotencyKeyId(UUID.fromString(key), user))
        .orElseThrow(() -> new AssertionError("no row for key " + key + " / " + user));
  }

  /** Inserts a row directly, already expired or not, bypassing the interceptor. */
  private IdempotencyKeyId seed(String user, String route, Duration untilExpiry) {
    IdempotencyKeyId id = new IdempotencyKeyId(UUID.randomUUID(), user);
    Instant now = clock.instant();
    int claimed =
        repository.claim(
            id.idempotencyKey(), id.userId(), route, "0".repeat(64), now, now.plus(untilExpiry));
    assertThat(claimed).isEqualTo(1);
    return id;
  }

  @Nested
  class Header {

    @Test
    void missingHeaderIs400ValidationErrorAndNothingRuns() throws Exception {
      mockMvc
          .perform(post("/orders", null, BODY))
          .andExpect(status().isBadRequest())
          .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
          .andExpect(jsonPath("$.type").value(URN + "validation-error"))
          .andExpect(jsonPath("$.errors[0].field").value(Idempotent.HEADER))
          .andExpect(jsonPath("$.errors[0].message").value("header obrigatório"));

      assertThat(controller.invocations("create")).isZero();
      assertThat(repository.count()).isZero();
    }

    @Test
    void malformedKeyIs400ValidationError() throws Exception {
      mockMvc
          .perform(post("/orders", "not-a-uuid", BODY))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.type").value(URN + "validation-error"))
          .andExpect(jsonPath("$.errors[0].field").value(Idempotent.HEADER))
          .andExpect(jsonPath("$.errors[0].message").value("deve ser um UUID"));

      assertThat(controller.invocations("create")).isZero();
      assertThat(repository.count()).isZero();
    }

    @Test
    void headerIsIgnoredOnRoutesWithoutTheAnnotation() throws Exception {
      String key = newKey();
      String first =
          mockMvc
              .perform(post("/orders/plain", key, BODY))
              .andReturn()
              .getResponse()
              .getContentAsString();
      String second =
          mockMvc
              .perform(post("/orders/plain", key, BODY))
              .andReturn()
              .getResponse()
              .getContentAsString();

      assertThat(first).isNotEqualTo(second);
      assertThat(controller.invocations("plain")).isEqualTo(2);
      assertThat(repository.count()).isZero();
    }
  }

  @Nested
  class Lifecycle {

    @Test
    void firstRequestExecutesAndStoresTheResponse() throws Exception {
      String key = newKey();

      MvcResult result =
          mockMvc
              .perform(post("/orders", key, BODY))
              .andExpect(status().isCreated())
              .andExpect(header().exists(HttpHeaders.LOCATION))
              .andExpect(jsonPath("$.name").value("Parafuso M6"))
              .andReturn();

      IdempotencyKeyEntity row = row(key, ANONYMOUS);
      assertThat(row.getState()).isEqualTo(IdempotencyState.COMPLETED);
      assertThat(row.getRoute()).isEqualTo("POST /orders");
      assertThat(row.getRequestHash())
          .matches("[0-9a-f]{64}")
          .isEqualTo(new CanonicalJsonHasher().hash(BODY.getBytes(StandardCharsets.UTF_8)));
      assertThat(row.getResponseStatus()).isEqualTo(201);
      assertThat(json(row.getResponseBody()))
          .isEqualTo(json(result.getResponse().getContentAsString()));
      JsonNode headers = json(row.getResponseHeaders());
      assertThat(headers.get(HttpHeaders.LOCATION).asString())
          .isEqualTo(result.getResponse().getHeader(HttpHeaders.LOCATION));
      assertThat(headers.get(HttpHeaders.CONTENT_TYPE).asString()).isEqualTo("application/json");
      assertThat(row.getExpiresAt()).isEqualTo(row.getCreatedAt().plus(properties.ttl()));
      assertThat(properties.ttl()).isEqualTo(Duration.ofHours(24));
    }

    @Test
    void sameKeyAndBodyReplaysTheStoredResponseWithoutExecutingAgain() throws Exception {
      String key = newKey();
      MvcResult first = mockMvc.perform(post("/orders", key, BODY)).andReturn();

      MvcResult second =
          mockMvc
              .perform(post("/orders", key, BODY))
              .andExpect(status().isCreated())
              .andExpect(content().contentType(MediaType.APPLICATION_JSON))
              .andReturn();

      assertThat(json(second.getResponse().getContentAsString()))
          .isEqualTo(json(first.getResponse().getContentAsString()));
      assertThat(second.getResponse().getHeader(HttpHeaders.LOCATION))
          .isEqualTo(first.getResponse().getHeader(HttpHeaders.LOCATION));
      assertThat(controller.invocations("create")).isEqualTo(1);
      assertThat(repository.count()).isEqualTo(1);
    }

    @Test
    void equivalentBodyWithDifferentKeyOrderAndWhitespaceReplays() throws Exception {
      String key = newKey();
      MvcResult first = mockMvc.perform(post("/orders", key, BODY)).andReturn();

      MvcResult second =
          mockMvc
              .perform(post("/orders", key, "{ \"quantity\" : 3 ,\n \"name\" : \"Parafuso M6\" }"))
              .andExpect(status().isCreated())
              .andReturn();

      assertThat(json(second.getResponse().getContentAsString()))
          .isEqualTo(json(first.getResponse().getContentAsString()));
      assertThat(controller.invocations("create")).isEqualTo(1);
    }

    @Test
    void sameKeyWithDifferentBodyIs422PayloadMismatch() throws Exception {
      String key = newKey();
      mockMvc.perform(post("/orders", key, BODY)).andExpect(status().isCreated());

      mockMvc
          .perform(post("/orders", key, "{\"name\":\"Parafuso M6\",\"quantity\":4}"))
          .andExpect(status().isUnprocessableContent())
          .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
          .andExpect(jsonPath("$.type").value(URN + "idempotency-key-payload-mismatch"))
          .andExpect(jsonPath("$.idempotencyKey").value(key));

      assertThat(controller.invocations("create")).isEqualTo(1);
      assertThat(row(key, ANONYMOUS).getResponseStatus()).isEqualTo(201);
    }

    @Test
    void sameKeyOnAnotherPathIs422AndTheSamePathReplays() throws Exception {
      String key = newKey();
      UUID first = UUID.randomUUID();
      UUID other = UUID.randomUUID();

      MvcResult confirmed =
          mockMvc
              .perform(post("/orders/" + first + "/confirm", key, null))
              .andExpect(status().isOk())
              .andReturn();
      assertThat(row(key, ANONYMOUS).getRoute()).isEqualTo("POST /orders/" + first + "/confirm");

      mockMvc
          .perform(post("/orders/" + other + "/confirm", key, null))
          .andExpect(status().isUnprocessableContent())
          .andExpect(jsonPath("$.type").value(URN + "idempotency-key-payload-mismatch"));

      MvcResult replayed =
          mockMvc
              .perform(post("/orders/" + first + "/confirm", key, null))
              .andExpect(status().isOk())
              .andReturn();
      assertThat(json(replayed.getResponse().getContentAsString()))
          .isEqualTo(json(confirmed.getResponse().getContentAsString()));
      assertThat(controller.invocations("confirm")).isEqualTo(1);
    }

    @Test
    void responseWithoutBodyIsStoredAndReplayed() throws Exception {
      String key = newKey();
      String path = "/orders/" + UUID.randomUUID() + "/archive";

      mockMvc.perform(post(path, key, null)).andExpect(status().isNoContent());
      mockMvc
          .perform(post(path, key, null))
          .andExpect(status().isNoContent())
          .andExpect(content().string(""));

      IdempotencyKeyEntity row = row(key, ANONYMOUS);
      assertThat(row.getResponseStatus()).isEqualTo(204);
      assertThat(row.getResponseBody()).isNull();
      assertThat(controller.invocations("archive")).isEqualTo(1);
    }

    @Test
    void businessRejectionIsStoredAndReplayedAsTheSame4xx() throws Exception {
      String key = newKey();

      MvcResult first =
          mockMvc
              .perform(post("/orders/reject", key, BODY))
              .andExpect(status().isUnprocessableContent())
              .andExpect(jsonPath("$.type").value(URN + "insufficient-stock"))
              .andReturn();

      MvcResult replayed =
          mockMvc
              .perform(post("/orders/reject", key, BODY))
              .andExpect(status().isUnprocessableContent())
              .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
              .andReturn();

      assertThat(json(replayed.getResponse().getContentAsString()))
          .isEqualTo(json(first.getResponse().getContentAsString()));
      assertThat(controller.invocations("reject")).isEqualTo(1);
      assertThat(row(key, ANONYMOUS).getResponseStatus()).isEqualTo(422);
    }

    @Test
    void serverErrorReleasesTheKeySoTheClientCanRetryWithIt() throws Exception {
      String key = newKey();

      mockMvc
          .perform(post("/orders/fail", key, BODY))
          .andExpect(status().isInternalServerError())
          .andExpect(jsonPath("$.type").value(URN + "internal-error"));
      assertThat(repository.count()).isZero();

      // Not 409: the retry with the same key runs the handler again.
      mockMvc.perform(post("/orders/fail", key, BODY)).andExpect(status().isInternalServerError());
      assertThat(controller.invocations("fail")).isEqualTo(2);
      assertThat(repository.count()).isZero();
    }

    @Test
    void nonJsonResponseIsNotStoredAndTheKeyIsReleased(CapturedOutput output) throws Exception {
      String key = newKey();

      String first =
          mockMvc
              .perform(post("/orders/text", key, BODY))
              .andExpect(status().isOk())
              .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_PLAIN))
              .andReturn()
              .getResponse()
              .getContentAsString();
      assertThat(repository.count()).isZero();
      assertThat(output.getOut()).contains("WARN").contains("non-JSON body");

      String second =
          mockMvc
              .perform(post("/orders/text", key, BODY))
              .andReturn()
              .getResponse()
              .getContentAsString();
      assertThat(second).startsWith("created ").isNotEqualTo(first);
      assertThat(controller.invocations("text")).isEqualTo(2);
    }
  }

  @Nested
  class Concurrency {

    @Test
    void keyStillBeingProcessedIs409InFlightUntilTheFirstRequestCompletes() throws Exception {
      String key = newKey();
      ExecutorService executor = Executors.newSingleThreadExecutor();
      try {
        Future<MvcResult> slow =
            executor.submit(() -> mockMvc.perform(post("/orders/slow", key, BODY)).andReturn());
        assertThat(controller.awaitSlowEntered(5, TimeUnit.SECONDS)).isTrue();
        assertThat(row(key, ANONYMOUS).getState()).isEqualTo(IdempotencyState.IN_FLIGHT);

        mockMvc
            .perform(post("/orders/slow", key, BODY))
            .andExpect(status().isConflict())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.type").value(URN + "idempotency-key-in-flight"))
            .andExpect(jsonPath("$.idempotencyKey").value(key));

        controller.releaseSlow();
        MvcResult first = slow.get(10, TimeUnit.SECONDS);
        assertThat(first.getResponse().getStatus()).isEqualTo(200);

        MvcResult replayed =
            mockMvc.perform(post("/orders/slow", key, BODY)).andExpect(status().isOk()).andReturn();
        assertThat(json(replayed.getResponse().getContentAsString()))
            .isEqualTo(json(first.getResponse().getContentAsString()));
        assertThat(controller.invocations("slow")).isEqualTo(1);
      } finally {
        controller.releaseSlow();
        executor.shutdownNow();
      }
    }
  }

  @Nested
  class UserScope {

    @Test
    void sameKeyFromDifferentUsersAreIndependent() throws Exception {
      String key = newKey();

      String alice =
          mockMvc
              .perform(
                  post("/orders", key, BODY).header(SampleServiceApplication.USER_HEADER, "alice"))
              .andExpect(status().isCreated())
              .andReturn()
              .getResponse()
              .getContentAsString();
      String bob =
          mockMvc
              .perform(
                  post("/orders", key, BODY).header(SampleServiceApplication.USER_HEADER, "bob"))
              .andExpect(status().isCreated())
              .andReturn()
              .getResponse()
              .getContentAsString();
      String anonymous =
          mockMvc
              .perform(post("/orders", key, BODY))
              .andExpect(status().isCreated())
              .andReturn()
              .getResponse()
              .getContentAsString();

      assertThat(alice).isNotEqualTo(bob).isNotEqualTo(anonymous);
      assertThat(controller.invocations("create")).isEqualTo(3);
      assertThat(repository.count()).isEqualTo(3);
      assertThat(json(row(key, "alice").getResponseBody())).isEqualTo(json(alice));
      assertThat(json(row(key, "bob").getResponseBody())).isEqualTo(json(bob));
      assertThat(json(row(key, ANONYMOUS).getResponseBody())).isEqualTo(json(anonymous));
    }
  }

  @Nested
  class Expiry {

    @Test
    void expiredInFlightKeyIsClaimedAsNew() throws Exception {
      IdempotencyKeyId id = seed(ANONYMOUS, "POST /orders", Duration.ofHours(-1));

      mockMvc
          .perform(post("/orders", id.idempotencyKey().toString(), BODY))
          .andExpect(status().isCreated());

      IdempotencyKeyEntity row = row(id.idempotencyKey().toString(), ANONYMOUS);
      assertThat(row.getState()).isEqualTo(IdempotencyState.COMPLETED);
      assertThat(row.getExpiresAt()).isAfter(clock.instant());
      assertThat(controller.invocations("create")).isEqualTo(1);
    }

    @Test
    void expiredCompletedKeyIsNotReplayed() throws Exception {
      IdempotencyKeyId id = seed(ANONYMOUS, "POST /orders", Duration.ofHours(-1));
      store.complete(id, new StoredResponse(201, "{\"stale\":true}", Map.of()));

      mockMvc
          .perform(post("/orders", id.idempotencyKey().toString(), BODY))
          .andExpect(status().isCreated())
          .andExpect(jsonPath("$.stale").doesNotExist())
          .andExpect(jsonPath("$.name").value("Parafuso M6"));

      assertThat(controller.invocations("create")).isEqualTo(1);
    }

    @Test
    void purgeExpiredRemovesOnlyExpiredRowsAcrossBatches() {
      for (int i = 0; i < 5; i++) {
        seed("user-" + i, "POST /orders", Duration.ofMinutes(-1));
      }
      IdempotencyKeyId live1 = seed("live-1", "POST /orders", Duration.ofHours(1));
      IdempotencyKeyId live2 = seed("live-2", "POST /orders", Duration.ofHours(1));

      // batch-size=2 → 2 + 2 + 1 across three transactions
      assertThat(cleaner.purgeExpired()).isEqualTo(5);

      assertThat(repository.count()).isEqualTo(2);
      assertThat(repository.existsById(live1)).isTrue();
      assertThat(repository.existsById(live2)).isTrue();
      assertThat(cleaner.purgeExpired()).isZero();
    }
  }

  @Nested
  class Wiring {

    @Autowired private ApplicationContext context;
    @Autowired private ScheduledAnnotationBeanPostProcessor scheduling;

    @Test
    void autoConfigurationRegistersEverythingFromAForeignBasePackage() {
      assertThat(context.getBean(IdempotencyKeyRepository.class)).isNotNull();
      assertThat(context.getBean(IdempotencyInterceptor.class)).isNotNull();
      assertThat(context.getBean("idempotencyFilter", FilterRegistrationBean.class).getFilter())
          .isInstanceOf(IdempotencyFilter.class);
      assertThat(context.getBean(IdempotencyUserResolver.class))
          .isNotInstanceOf(PrincipalNameUserResolver.class); // the service's override won
    }

    @Test
    void cleanupIsScheduledWithTheConfiguredCron() {
      assertThat(scheduling.getScheduledTasks())
          .map(task -> task.getTask())
          .filteredOn(task -> task instanceof CronTask)
          .map(task -> ((CronTask) task).getExpression())
          .contains("0 30 3 * * *");
    }
  }
}
