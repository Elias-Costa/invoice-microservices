package dev.eliascosta.invoice.gateway.config;

import java.util.UUID;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter implements WebFilter {

  private static final String TRACE_ID_HEADER = "X-Trace-Id";

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
    String traceId = UUID.randomUUID().toString();
    var response = exchange.getResponse();
    response.beforeCommit(
        () -> Mono.fromRunnable(() -> response.getHeaders().set(TRACE_ID_HEADER, traceId)));
    return chain.filter(
        exchange
            .mutate()
            .request(request -> request.headers(headers -> headers.set(TRACE_ID_HEADER, traceId)))
            .build());
  }
}
