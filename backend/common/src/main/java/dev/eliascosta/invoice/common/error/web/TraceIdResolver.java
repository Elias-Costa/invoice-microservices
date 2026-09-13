package dev.eliascosta.invoice.common.error.web;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
import org.slf4j.MDC;

/**
 * Resolves the {@code traceId} exposed in every {@code ProblemDetail} (RNF-OBS-05).
 *
 * <p>Order (D-010): the {@value #MDC_KEY} MDC entry populated by Micrometer Tracing, then the
 * {@value #HEADER} request header added by the gateway, otherwise empty — the property is omitted
 * rather than filled with a value that correlates with nothing.
 */
public class TraceIdResolver {

  public static final String MDC_KEY = "traceId";
  public static final String HEADER = "X-Trace-Id";

  public Optional<String> resolve(HttpServletRequest request) {
    return nonBlank(MDC.get(MDC_KEY)).or(() -> nonBlank(request.getHeader(HEADER)));
  }

  private static Optional<String> nonBlank(String value) {
    return value == null || value.isBlank() ? Optional.empty() : Optional.of(value.strip());
  }
}
