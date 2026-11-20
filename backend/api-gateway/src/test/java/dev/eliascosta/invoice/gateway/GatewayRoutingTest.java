package dev.eliascosta.invoice.gateway;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.util.UUID;
import java.util.function.Consumer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GatewayRoutingTest {

  private static final String FRONTEND = "http://localhost:4200";
  private static final String TRACE_ID = "X-Trace-Id";
  private static final String IDEMPOTENCY_KEY = "Idempotency-Key";
  private static final String PRODUCTS = "/api/stock/products";
  private static final String INVOICES = "/api/billing/invoices";

  private static final HttpServer STOCK = startService(GatewayRoutingTest::stockRoutes);
  private static final HttpServer BILLING = startService(GatewayRoutingTest::billingRoutes);

  @LocalServerPort private int port;
  private WebTestClient client;

  @DynamicPropertySource
  static void serviceUrls(DynamicPropertyRegistry registry) {
    registry.add("invoice.stock.base-url", () -> url(STOCK.getAddress().getPort()));
    registry.add("invoice.billing.base-url", () -> url(BILLING.getAddress().getPort()));
  }

  @BeforeEach
  void bindClient() {
    client = WebTestClient.bindToServer().baseUrl(url(port)).build();
  }

  @AfterAll
  static void stopServices() {
    STOCK.stop(0);
    BILLING.stop(0);
  }

  @Test
  void forwardsStockRequestWithItsPrefixAndQuery() {
    String path = PRODUCTS + "?active=false&page=2";

    client
        .get()
        .uri(path)
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody(String.class)
        .isEqualTo("stock GET " + path);
  }

  @Test
  void forwardsBillingRequestAndExposesLocationAndTraceIdToTheFrontend() {
    String idempotencyKey = UUID.randomUUID().toString();
    String body = "{\"items\":[]}";

    client
        .post()
        .uri(INVOICES)
        .contentType(MediaType.APPLICATION_JSON)
        .header(IDEMPOTENCY_KEY, idempotencyKey)
        .header(HttpHeaders.ORIGIN, FRONTEND)
        .bodyValue(body)
        .exchange()
        .expectStatus()
        .isCreated()
        .expectHeader()
        .valueEquals(HttpHeaders.LOCATION, INVOICES + "/created")
        .expectHeader()
        .valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, FRONTEND)
        .expectHeader()
        .valueEquals(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, "Location, X-Trace-Id")
        .expectHeader()
        .doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS)
        .expectBody(String.class)
        .isEqualTo("billing POST " + INVOICES + " " + idempotencyKey + " " + body);
  }

  @Test
  void usesTheGatewayTraceIdInTheDownstreamProblemAndResponseHeader() {
    var result =
        client
            .get()
            .uri(PRODUCTS + "/missing")
            .header(TRACE_ID, "client-value")
            .exchange()
            .expectStatus()
            .isNotFound()
            .expectHeader()
            .contentType(MediaType.APPLICATION_PROBLEM_JSON)
            .expectBody(String.class)
            .returnResult();

    String traceId = result.getResponseHeaders().getFirst(TRACE_ID);
    assertThat(traceId).isNotNull();
    assertThat(UUID.fromString(traceId).version()).isEqualTo(4);
    assertThat(result.getResponseHeaders().get(TRACE_ID)).containsExactly(traceId);
    assertThat(JsonPath.<String>read(result.getResponseBody(), "$.traceId")).isEqualTo(traceId);
  }

  @Test
  void allowsPreflightForWritingFromTheFrontend() {
    preflight(FRONTEND, "POST", "Content-Type, Idempotency-Key")
        .expectStatus()
        .isOk()
        .expectHeader()
        .valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, FRONTEND)
        .expectHeader()
        .valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS, "GET,POST,PUT,DELETE")
        .expectHeader()
        .valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS, "Content-Type, Idempotency-Key")
        .expectBody()
        .isEmpty();
  }

  @Test
  void rejectsPreflightFromAnotherOrigin() {
    preflight("http://127.0.0.1:4200", "POST", "Content-Type")
        .expectStatus()
        .isForbidden()
        .expectHeader()
        .doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN);
  }

  @Test
  void rejectsPreflightForMethodsAndHeadersOutsideTheApiPolicy() {
    preflight(FRONTEND, "PATCH", "Content-Type").expectStatus().isForbidden();
    preflight(FRONTEND, "POST", "Authorization").expectStatus().isForbidden();
  }

  @Test
  void reportsActuatorHealthWithATraceId() {
    client
        .get()
        .uri("/actuator/health")
        .exchange()
        .expectStatus()
        .isOk()
        .expectHeader()
        .exists(TRACE_ID)
        .expectBody()
        .jsonPath("$.status")
        .isEqualTo("UP");
  }

  private WebTestClient.ResponseSpec preflight(String origin, String method, String headers) {
    return client
        .options()
        .uri(INVOICES)
        .header(HttpHeaders.ORIGIN, origin)
        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, method)
        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, headers)
        .exchange();
  }

  private static void stockRoutes(HttpServer server) {
    server.createContext(
        PRODUCTS,
        exchange ->
            send(
                exchange,
                200,
                MediaType.TEXT_PLAIN_VALUE,
                "stock " + exchange.getRequestMethod() + " " + exchange.getRequestURI()));
    server.createContext(
        PRODUCTS + "/missing",
        exchange -> {
          String traceId = exchange.getRequestHeaders().getFirst(TRACE_ID);
          exchange.getResponseHeaders().set(TRACE_ID, "downstream-value");
          send(
              exchange,
              404,
              MediaType.APPLICATION_PROBLEM_JSON_VALUE,
              "{\"status\":404,\"traceId\":\"" + traceId + "\"}");
        });
  }

  private static void billingRoutes(HttpServer server) {
    server.createContext(
        INVOICES,
        exchange -> {
          String key = exchange.getRequestHeaders().getFirst(IDEMPOTENCY_KEY);
          String body = new String(exchange.getRequestBody().readAllBytes(), UTF_8);
          exchange.getResponseHeaders().set(HttpHeaders.LOCATION, INVOICES + "/created");
          String request = exchange.getRequestMethod() + " " + exchange.getRequestURI();
          send(
              exchange,
              201,
              MediaType.TEXT_PLAIN_VALUE,
              "billing " + request + " " + key + " " + body);
        });
  }

  private static HttpServer startService(Consumer<HttpServer> registerRoutes) {
    try {
      HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
      registerRoutes.accept(server);
      server.start();
      return server;
    } catch (IOException exception) {
      throw new UncheckedIOException(exception);
    }
  }

  private static void send(HttpExchange exchange, int status, String mediaType, String body)
      throws IOException {
    try (exchange) {
      byte[] bytes = body.getBytes(UTF_8);
      exchange.getResponseHeaders().set(HttpHeaders.CONTENT_TYPE, mediaType);
      exchange.sendResponseHeaders(status, bytes.length);
      exchange.getResponseBody().write(bytes);
    }
  }

  private static String url(int port) {
    return "http://127.0.0.1:" + port;
  }
}
