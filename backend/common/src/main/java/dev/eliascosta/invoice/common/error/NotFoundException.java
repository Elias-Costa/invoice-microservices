package dev.eliascosta.invoice.common.error;

import java.util.Map;

/**
 * Resource identified by the request does not exist (e.g. {@code product-not-found}).
 *
 * <p>Mapped to <strong>404 Not Found</strong> by the {@code ProblemDetailExceptionHandler}.
 */
public non-sealed class NotFoundException extends DomainException {

  public NotFoundException(String type, String title, String detail) {
    super(type, title, detail);
  }

  public NotFoundException(
      String type, String title, String detail, Map<String, Object> properties) {
    super(type, title, detail, properties);
  }

  public NotFoundException(String type, String title, String detail, Throwable cause) {
    super(type, title, detail, cause);
  }

  public NotFoundException(
      String type, String title, String detail, Map<String, Object> properties, Throwable cause) {
    super(type, title, detail, properties, cause);
  }
}
