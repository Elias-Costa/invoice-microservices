package dev.eliascosta.invoice.billing.adapter.out.http;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.serverError;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeout;

import com.github.tomakehurst.wiremock.http.Fault;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import dev.eliascosta.invoice.billing.domain.port.out.StockGateway;
import dev.eliascosta.invoice.billing.domain.port.out.StockGateway.ProductSnapshot;
import dev.eliascosta.invoice.common.error.UpstreamUnavailableException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreaker.State;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.JsonNode;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers
class StockGatewayIT {

  private static final String BASE_PATH = "/api/stock/products/batch";
  private static final String INSTANCE = "stockService";
  private static final UUID PRODUCT = UUID.fromString("719c2177-db22-4b8c-acfd-8c4002707d34");

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

  @Autowired private StockGateway gateway;
  @Autowired private CircuitBreakerRegistry circuitBreakers;
  @Autowired private RetryRegistry retries;
  @LocalServerPort private int port;

  private CircuitBreaker breaker;
  private RestClient actuator;

  @BeforeEach
  void setUp() {
    breaker = circuitBreakers.circuitBreaker(INSTANCE);
    breaker.reset();
    actuator = RestClient.create("http://127.0.0.1:" + port);
  }

  @Test
  void readsStockInBatchesOfAtMost100AndPreservesInactiveProducts() {
    List<UUID> ids =
        new ArrayList<>(IntStream.range(1, 101).mapToObj(i -> new UUID(0, i)).toList());
    ids.add(PRODUCT);
    ids.add(PRODUCT);
    String firstBatch =
        ids.subList(0, 100).stream().map(UUID::toString).collect(Collectors.joining(","));
    UUID firstProduct = ids.getFirst();
    stock.stubFor(
        get(urlPathEqualTo(BASE_PATH))
            .withQueryParam("ids", equalTo(firstBatch))
            .willReturn(okJson(productResponse(firstProduct, true))));
    stock.stubFor(
        get(urlPathEqualTo(BASE_PATH))
            .withQueryParam("ids", equalTo(PRODUCT.toString()))
            .willReturn(okJson(productResponse(PRODUCT, false))));

    assertThat(gateway.findProducts(List.of())).isEmpty();
    assertThat(gateway.findProducts(ids))
        .containsExactly(
            new ProductSnapshot(firstProduct, "P-01", "Produto", new BigDecimal("19.90"), 2, true),
            new ProductSnapshot(PRODUCT, "P-01", "Produto", new BigDecimal("19.90"), 2, false));
    stock.verify(2, getRequestedFor(urlPathEqualTo(BASE_PATH)));
  }

  @Test
  void retriesTwoServerErrorsThenReturnsProductsAndRecordsOneSuccess() {
    stock.stubFor(
        get(urlPathEqualTo(BASE_PATH))
            .inScenario("Retry")
            .whenScenarioStateIs(Scenario.STARTED)
            .willSetStateTo("First failure")
            .willReturn(serverError()));
    stock.stubFor(
        get(urlPathEqualTo(BASE_PATH))
            .inScenario("Retry")
            .whenScenarioStateIs("First failure")
            .willSetStateTo("Second failure")
            .willReturn(serverError()));
    stock.stubFor(
        get(urlPathEqualTo(BASE_PATH))
            .inScenario("Retry")
            .whenScenarioStateIs("Second failure")
            .willReturn(okJson(productResponse(PRODUCT, true))));

    assertThat(gateway.findProducts(List.of(PRODUCT)))
        .extracting(ProductSnapshot::id)
        .containsExactly(PRODUCT);
    stock.verify(3, getRequestedFor(urlPathEqualTo(BASE_PATH)));
    assertThat(breaker.getMetrics().getNumberOfSuccessfulCalls()).isEqualTo(1);
    assertThat(breaker.getMetrics().getNumberOfFailedCalls()).isZero();
  }

  @Test
  void boundsEachAttemptToTwoSecondsAndRetriesTimeouts() {
    stock.stubFor(get(urlPathEqualTo(BASE_PATH)).willReturn(okJson("[]").withFixedDelay(15_000)));

    assertTimeout(Duration.ofSeconds(9), () -> assertUnavailable());

    stock.verify(3, getRequestedFor(urlPathEqualTo(BASE_PATH)));
    assertThat(breaker.getMetrics().getNumberOfFailedCalls()).isEqualTo(1);
    assertThat(breaker.getState()).isEqualTo(State.CLOSED);
  }

  @Test
  void retriesIoFailuresBeforeReportingStockUnavailable() {
    stock.stubFor(
        get(urlPathEqualTo(BASE_PATH)).willReturn(aResponse().withFault(Fault.EMPTY_RESPONSE)));
    Retry retry = retries.retry(INSTANCE);
    long exhausted = retry.getMetrics().getNumberOfFailedCallsWithRetryAttempt();

    assertThat(assertUnavailable()).hasCauseInstanceOf(ResourceAccessException.class);

    assertThat(retry.getMetrics().getNumberOfFailedCallsWithRetryAttempt())
        .isEqualTo(exhausted + 1);
    assertThat(breaker.getMetrics().getNumberOfFailedCalls()).isEqualTo(1);
  }

  @Test
  void mapsStock4xxToUnavailableWithoutRetryOrCircuitSample() {
    stock.stubFor(get(urlPathEqualTo(BASE_PATH)).willReturn(aResponse().withStatus(400)));

    assertThat(assertUnavailable()).hasCauseInstanceOf(HttpClientErrorException.class);

    stock.verify(1, getRequestedFor(urlPathEqualTo(BASE_PATH)));
    assertThat(breaker.getMetrics().getNumberOfBufferedCalls()).isZero();
  }

  @Test
  void opensAfterFiveFailuresInTenCallsAndRecoversThroughHalfOpen() {
    stock.stubFor(get(urlPathEqualTo(BASE_PATH)).willReturn(serverError()));
    for (int attempt = 0; attempt < 5; attempt++) {
      assertThat(assertUnavailable()).hasCauseInstanceOf(HttpServerErrorException.class);
      assertThat(breaker.getState()).isEqualTo(State.CLOSED);
    }

    stock.resetMappings();
    stock.stubFor(get(urlPathEqualTo(BASE_PATH)).willReturn(okJson("[]")));
    for (int attempt = 0; attempt < 5; attempt++) {
      assertThat(gateway.findProducts(List.of(PRODUCT))).isEmpty();
    }
    assertThat(breaker.getState()).isEqualTo(State.OPEN);
    assertThat(breaker.getMetrics().getNumberOfFailedCalls()).isEqualTo(5);
    stock.verify(20, getRequestedFor(urlPathEqualTo(BASE_PATH)));

    assertThat(assertTimeout(Duration.ofMillis(500), () -> assertUnavailable()))
        .hasCauseInstanceOf(CallNotPermittedException.class);
    stock.verify(20, getRequestedFor(urlPathEqualTo(BASE_PATH)));
    assertCircuitObservability("OPEN");

    awaitHalfOpen();
    assertCircuitObservability("HALF_OPEN");
    stock.resetMappings();
    stock.stubFor(get(urlPathEqualTo(BASE_PATH)).willReturn(serverError()));
    for (int probe = 0; probe < 3; probe++) {
      assertUnavailable();
    }
    assertThat(breaker.getState()).isEqualTo(State.OPEN);

    awaitHalfOpen();
    stock.resetMappings();
    stock.stubFor(get(urlPathEqualTo(BASE_PATH)).willReturn(okJson("[]")));
    for (int probe = 0; probe < 3; probe++) {
      assertThat(breaker.getState()).isEqualTo(State.HALF_OPEN);
      assertThat(gateway.findProducts(List.of(PRODUCT))).isEmpty();
    }
    assertThat(breaker.getState()).isEqualTo(State.CLOSED);
    assertCircuitObservability("CLOSED");
  }

  private UpstreamUnavailableException assertUnavailable() {
    var exception =
        assertThrows(
            UpstreamUnavailableException.class, () -> gateway.findProducts(List.of(PRODUCT)));
    assertThat(exception.getType()).isEqualTo("stock-service-unavailable");
    return exception;
  }

  private void awaitHalfOpen() {
    await().atMost(Duration.ofSeconds(12)).until(() -> breaker.getState() == State.HALF_OPEN);
  }

  private void assertCircuitObservability(String state) {
    JsonNode health = actuator.get().uri("/actuator/health").retrieve().body(JsonNode.class);
    assertThat(health).isNotNull();
    assertThat(
            health.at("/components/circuitBreakers/details/stockService/details/state").asString())
        .isEqualTo(state);
    String metrics = actuator.get().uri("/actuator/prometheus").retrieve().body(String.class);
    assertThat(metrics)
        .contains(
            "resilience4j_circuitbreaker_state{name=\"stockService\",state=\""
                + state.toLowerCase(Locale.ROOT)
                + "\"} 1.0");
  }

  private static String productResponse(UUID id, boolean active) {
    return """
    [{"id":"%s","code":"P-01","description":"Produto","unitPrice":19.90,"balance":2,"active":%s}]
    """
        .formatted(id, active);
  }
}
