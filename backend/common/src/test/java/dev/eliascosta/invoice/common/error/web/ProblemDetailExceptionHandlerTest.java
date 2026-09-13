package dev.eliascosta.invoice.common.error.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.ResultMatcher;

/**
 * Contract of the shared error handling ({@code requisitos.md} §8.8, RNF-SEC-07, D-009, D-010,
 * D-011), exercised through MockMvc with {@link ErrorTestController}.
 */
@WebMvcTest
@Import(ErrorTestController.class) // CommonTestApplication does not component-scan on purpose
@ImportAutoConfiguration(ErrorHandlingAutoConfiguration.class) // the real registration path
class ProblemDetailExceptionHandlerTest {

  private static final String URN = "urn:problem-type:";

  @Autowired private MockMvc mockMvc;

  @AfterEach
  void clearMdc() {
    MDC.clear();
  }

  /** Members every problem must carry, whatever produced it. */
  private static ResultMatcher[] problemShape(int status, String instance) {
    return new ResultMatcher[] {
      content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON),
      jsonPath("$.status").value(status),
      jsonPath("$.instance").value(instance),
      jsonPath("$.title").isString(),
      jsonPath("$.detail").isString(),
      jsonPath("$.timestamp").isString(),
    };
  }

  private static ResultMatcher type(String slug) {
    return jsonPath("$.type").value(URN + slug);
  }

  @Nested
  class DomainExceptions {

    @Test
    void notFoundMapsTo404WithCatalogueType() throws Exception {
      mockMvc
          .perform(get("/test/not-found"))
          .andExpect(status().isNotFound())
          .andExpectAll(problemShape(404, "/test/not-found"))
          .andExpect(type("product-not-found"))
          .andExpect(jsonPath("$.title").value("Produto não encontrado"))
          .andExpect(jsonPath("$.detail").value("Produto 7b1e não existe."));
    }

    @Test
    void conflictMapsTo409() throws Exception {
      mockMvc
          .perform(get("/test/conflict"))
          .andExpect(status().isConflict())
          .andExpectAll(problemShape(409, "/test/conflict"))
          .andExpect(type("product-code-already-exists"));
    }

    @Test
    void invalidStateMapsTo409AndExposesExtensionProperties() throws Exception {
      mockMvc
          .perform(get("/test/invalid-state"))
          .andExpect(status().isConflict())
          .andExpectAll(problemShape(409, "/test/invalid-state"))
          .andExpect(type("invalid-invoice-status"))
          .andExpect(jsonPath("$.currentStatus").value("PROCESSING"))
          .andExpect(jsonPath("$.allowedTransitions", containsInAnyOrder("CLOSED", "OPEN")));
    }

    @Test
    void businessRuleMapsTo422() throws Exception {
      mockMvc
          .perform(get("/test/business-rule"))
          .andExpect(status().isUnprocessableContent())
          .andExpectAll(problemShape(422, "/test/business-rule"))
          .andExpect(type("insufficient-stock"))
          .andExpect(jsonPath("$.title").value("Saldo insuficiente"))
          .andExpect(
              jsonPath("$.detail")
                  .value("Saldo insuficiente para PARAFUSO-M6: solicitado 3, disponível 1"));
    }

    @Test
    void invalidRequestMapsTo400() throws Exception {
      mockMvc
          .perform(get("/test/invalid-request"))
          .andExpect(status().isBadRequest())
          .andExpectAll(problemShape(400, "/test/invalid-request"))
          .andExpect(type("duplicate-invoice-item"));
    }

    @Test
    void upstreamUnavailableMapsTo503() throws Exception {
      mockMvc
          .perform(get("/test/upstream"))
          .andExpect(status().isServiceUnavailable())
          .andExpectAll(problemShape(503, "/test/upstream"))
          .andExpect(type("stock-service-unavailable"));
    }
  }

  @Nested
  class Validation {

    @Test
    void invalidBodyListsFieldViolations() throws Exception {
      mockMvc
          .perform(
              post("/test/validation")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"name\":\" \",\"quantity\":0}"))
          .andExpect(status().isBadRequest())
          .andExpectAll(problemShape(400, "/test/validation"))
          .andExpect(type("validation-error"))
          .andExpect(jsonPath("$.title").value("Dados inválidos"))
          .andExpect(jsonPath("$.errors", hasSize(2)))
          .andExpect(jsonPath("$.errors[*].field", containsInAnyOrder("name", "quantity")))
          .andExpect(jsonPath("$.errors[0].message").isString())
          .andExpect(jsonPath("$.errors[1].message").isString());
    }

    @Test
    void validBodyPassesThrough() throws Exception {
      mockMvc
          .perform(
              post("/test/validation")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"name\":\"Parafuso\",\"quantity\":3}"))
          .andExpect(status().isOk());
    }

    @Test
    void malformedJsonIsValidationErrorWithoutParserInternals() throws Exception {
      mockMvc
          .perform(
              post("/test/validation")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"name\": "))
          .andExpect(status().isBadRequest())
          .andExpectAll(problemShape(400, "/test/validation"))
          .andExpect(type("validation-error"))
          .andExpect(jsonPath("$.detail").value("Corpo da requisição ilegível ou malformado."))
          .andExpect(jsonPath("$.errors").doesNotExist());
    }

    @Test
    void constrainedRequestParamListsParameterName() throws Exception {
      mockMvc
          .perform(get("/test/params").param("page", "-1"))
          .andExpect(status().isBadRequest())
          .andExpectAll(problemShape(400, "/test/params"))
          .andExpect(type("validation-error"))
          .andExpect(jsonPath("$.errors", hasSize(1)))
          .andExpect(jsonPath("$.errors[0].field").value("page"))
          .andExpect(jsonPath("$.errors[0].message").isString());
    }

    @Test
    void typeMismatchNamesTheParameter() throws Exception {
      mockMvc
          .perform(get("/test/params").param("page", "abc"))
          .andExpect(status().isBadRequest())
          .andExpectAll(problemShape(400, "/test/params"))
          .andExpect(type("validation-error"))
          .andExpect(jsonPath("$.detail").value("Parâmetro 'page' com valor inválido."));
    }

    @Test
    void missingParameterNamesTheParameter() throws Exception {
      mockMvc
          .perform(get("/test/params"))
          .andExpect(status().isBadRequest())
          .andExpectAll(problemShape(400, "/test/params"))
          .andExpect(type("validation-error"))
          .andExpect(jsonPath("$.detail").value("Parâmetro obrigatório 'page' ausente."));
    }

    @Test
    void otherSpringBadRequestsFallBackToValidationError() throws Exception {
      // Missing @RequestHeader: handled by ResponseEntityExceptionHandler, not overridden here
      mockMvc
          .perform(get("/test/header"))
          .andExpect(status().isBadRequest())
          .andExpectAll(problemShape(400, "/test/header"))
          .andExpect(type("validation-error"))
          .andExpect(jsonPath("$.title").value("Dados inválidos"));
    }

    @Test
    void constraintViolationExceptionListsLeafProperties() throws Exception {
      mockMvc
          .perform(get("/test/constraint-violation"))
          .andExpect(status().isBadRequest())
          .andExpectAll(problemShape(400, "/test/constraint-violation"))
          .andExpect(type("validation-error"))
          .andExpect(jsonPath("$.errors", hasSize(2)))
          .andExpect(jsonPath("$.errors[*].field", containsInAnyOrder("name", "quantity")));
    }
  }

  @Nested
  class Persistence {

    @ParameterizedTest
    @ValueSource(strings = {"/test/optimistic-lock/spring", "/test/optimistic-lock/jpa"})
    void optimisticLockMapsTo409(String path) throws Exception {
      mockMvc
          .perform(get(path))
          .andExpect(status().isConflict())
          .andExpectAll(problemShape(409, path))
          .andExpect(type("optimistic-lock-conflict"))
          .andExpect(jsonPath("$.title").value("Conflito de edição concorrente"));
    }

    @Test
    void uniqueViolationBySqlStateMapsTo409() throws Exception {
      mockMvc
          .perform(get("/test/data-integrity").param("sqlState", "23505"))
          .andExpect(status().isConflict())
          .andExpectAll(problemShape(409, "/test/data-integrity"))
          .andExpect(type("unique-constraint-violation"))
          .andExpect(jsonPath("$.detail", not(containsString("constraint violated"))));
    }

    @Test
    void duplicateKeyExceptionMapsTo409() throws Exception {
      mockMvc
          .perform(get("/test/duplicate-key"))
          .andExpect(status().isConflict())
          .andExpectAll(problemShape(409, "/test/duplicate-key"))
          .andExpect(type("unique-constraint-violation"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"23503", "23514", "23502", "99999"})
    void otherIntegrityViolationsMapTo422(String sqlState) throws Exception {
      mockMvc
          .perform(get("/test/data-integrity").param("sqlState", sqlState))
          .andExpect(status().isUnprocessableContent())
          .andExpectAll(problemShape(422, "/test/data-integrity"))
          .andExpect(type("data-integrity-violation"));
    }
  }

  @Nested
  class Fallbacks {

    @Test
    void unexpectedExceptionMapsTo500WithoutInternals() throws Exception {
      mockMvc
          .perform(get("/test/unexpected"))
          .andExpect(status().isInternalServerError())
          .andExpectAll(problemShape(500, "/test/unexpected"))
          .andExpect(type("internal-error"))
          .andExpect(jsonPath("$.title").value("Erro interno"))
          .andExpect(jsonPath("$.detail", not(containsString("SELECT"))))
          .andExpect(jsonPath("$.detail", not(containsString("secret"))))
          .andExpect(
              content().string(not(containsString(ErrorTestController.UNEXPECTED_INTERNALS))))
          .andExpect(content().string(not(containsString("IllegalStateException"))));
    }

    @Test
    void methodNotAllowedKeepsSpringProblemEnriched() throws Exception {
      mockMvc
          .perform(post("/test/not-found"))
          .andExpect(status().isMethodNotAllowed())
          .andExpectAll(problemShape(405, "/test/not-found"))
          // Spring 7 omits the default "about:blank" type (RFC 9457 §3.1.1: absent == about:blank)
          .andExpect(jsonPath("$.type").doesNotExist());
    }

    @Test
    void unknownRouteIs404WithSpringProblemEnriched() throws Exception {
      mockMvc
          .perform(get("/test/does-not-exist"))
          .andExpect(status().isNotFound())
          .andExpectAll(problemShape(404, "/test/does-not-exist"))
          // Spring 7 omits the default "about:blank" type (RFC 9457 §3.1.1: absent == about:blank)
          .andExpect(jsonPath("$.type").doesNotExist());
    }
  }

  @Nested
  class Enrichment {

    @Test
    void traceIdComesFromMdcWhenPresent() throws Exception {
      MDC.put(TraceIdResolver.MDC_KEY, "4bf92f3577b34da6a3ce929d0e0e4736");
      mockMvc
          .perform(get("/test/not-found").header(TraceIdResolver.HEADER, "from-header"))
          .andExpect(jsonPath("$.traceId").value("4bf92f3577b34da6a3ce929d0e0e4736"));
    }

    @Test
    void traceIdFallsBackToRequestHeader() throws Exception {
      mockMvc
          .perform(get("/test/not-found").header(TraceIdResolver.HEADER, "from-header"))
          .andExpect(jsonPath("$.traceId").value("from-header"));
    }

    @Test
    void traceIdIsOmittedWhenUnavailable() throws Exception {
      mockMvc.perform(get("/test/not-found")).andExpect(jsonPath("$.traceId").doesNotExist());
    }

    @Test
    void timestampIsAnIsoInstantCloseToNow() throws Exception {
      Instant before = Instant.now();
      ResultActions result = mockMvc.perform(get("/test/not-found"));
      String body = result.andReturn().getResponse().getContentAsString();
      String timestamp = body.replaceAll(".*\"timestamp\":\"([^\"]+)\".*", "$1");
      Instant parsed = Instant.parse(timestamp);
      assertThat(parsed).isBetween(before.minusSeconds(1), Instant.now().plusSeconds(1));
    }
  }
}
