package dev.eliascosta.invoice.common.error;

import java.util.Map;

/**
 * A dependency this service needs is unreachable, timed out or its circuit breaker is open (e.g.
 * {@code stock-service-unavailable}).
 *
 * <p>Mapped to <strong>503 Service Unavailable</strong> by the {@code
 * ProblemDetailExceptionHandler}.
 */
public non-sealed class UpstreamUnavailableException extends DomainException {

  public UpstreamUnavailableException(String type, String title, String detail) {
    super(type, title, detail);
  }

  public UpstreamUnavailableException(
      String type, String title, String detail, Map<String, Object> properties) {
    super(type, title, detail, properties);
  }

  public UpstreamUnavailableException(String type, String title, String detail, Throwable cause) {
    super(type, title, detail, cause);
  }

  public UpstreamUnavailableException(
      String type, String title, String detail, Map<String, Object> properties, Throwable cause) {
    super(type, title, detail, properties, cause);
  }
}
