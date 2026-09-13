package dev.eliascosta.invoice.common.error;

import java.util.Map;

/**
 * A business rule rejected a well-formed request (e.g. {@code insufficient-stock}, {@code
 * product-inactive}, {@code idempotency-key-payload-mismatch}). Never retried by messaging
 * consumers.
 *
 * <p>Mapped to <strong>422 Unprocessable Content</strong> by the {@code
 * ProblemDetailExceptionHandler}.
 */
public non-sealed class BusinessRuleException extends DomainException {

  public BusinessRuleException(String type, String title, String detail) {
    super(type, title, detail);
  }

  public BusinessRuleException(
      String type, String title, String detail, Map<String, Object> properties) {
    super(type, title, detail, properties);
  }

  public BusinessRuleException(String type, String title, String detail, Throwable cause) {
    super(type, title, detail, cause);
  }

  public BusinessRuleException(
      String type, String title, String detail, Map<String, Object> properties, Throwable cause) {
    super(type, title, detail, properties, cause);
  }
}
