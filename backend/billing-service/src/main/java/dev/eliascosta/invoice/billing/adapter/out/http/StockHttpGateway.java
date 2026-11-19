package dev.eliascosta.invoice.billing.adapter.out.http;

import dev.eliascosta.invoice.billing.domain.port.out.StockGateway;
import dev.eliascosta.invoice.common.error.UpstreamUnavailableException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeoutException;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;

@Component
class StockHttpGateway implements StockGateway {

  private static final int BATCH_SIZE = 100;

  private final StockHttpClient client;

  StockHttpGateway(StockHttpClient client) {
    this.client = client;
  }

  @Override
  public List<ProductSnapshot> findProducts(Collection<UUID> ids) {
    List<UUID> distinctIds = ids.stream().distinct().toList();
    List<ProductSnapshot> products = new ArrayList<>();
    for (int start = 0; start < distinctIds.size(); start += BATCH_SIZE) {
      int end = Math.min(start + BATCH_SIZE, distinctIds.size());
      try {
        products.addAll(client.findBatch(distinctIds.subList(start, end)).join());
      } catch (CompletionException exception) {
        Throwable cause = exception.getCause();
        if (cause instanceof RestClientException
            || cause instanceof TimeoutException
            || cause instanceof CallNotPermittedException) {
          throw new UpstreamUnavailableException(
              "stock-service-unavailable",
              "Estoque indisponível",
              "Serviço de estoque indisponível. Tente novamente em instantes.",
              cause);
        }
        throw exception;
      }
    }
    return List.copyOf(products);
  }
}
