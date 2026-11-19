package dev.eliascosta.invoice.billing.adapter.out.http;

import dev.eliascosta.invoice.billing.domain.port.out.StockGateway.ProductSnapshot;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
class StockHttpClient {

  private static final String INSTANCE = "stockService";
  private static final ParameterizedTypeReference<List<ProductSnapshot>> BATCH_TYPE =
      new ParameterizedTypeReference<>() {};

  private final RestClient client;
  private final ExecutorService executor;

  StockHttpClient(
      @Qualifier("stockRestClient") RestClient client,
      @Qualifier("stockHttpExecutor") ExecutorService executor) {
    this.client = client;
    this.executor = executor;
  }

  @CircuitBreaker(name = INSTANCE)
  @Retry(name = INSTANCE)
  @TimeLimiter(name = INSTANCE)
  public CompletableFuture<List<ProductSnapshot>> findBatch(List<UUID> ids) {
    return CompletableFuture.supplyAsync(() -> requestBatch(ids), executor);
  }

  private List<ProductSnapshot> requestBatch(List<UUID> ids) {
    String idParameter = ids.stream().map(UUID::toString).collect(Collectors.joining(","));
    List<ProductSnapshot> products =
        client
            .get()
            .uri(
                uri -> uri.path("/api/stock/products/batch").queryParam("ids", idParameter).build())
            .accept(MediaType.APPLICATION_JSON)
            .retrieve()
            .body(BATCH_TYPE);
    if (products == null) {
      throw new RestClientException("Stock returned an empty response for a product batch");
    }
    return products;
  }
}
