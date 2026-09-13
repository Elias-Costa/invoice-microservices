package dev.eliascosta.invoice.common.error;

import java.util.Map;

/**
 * Operation is not allowed in the aggregate's current state (e.g. {@code invalid-invoice-status},
 * carrying {@code currentStatus} and {@code allowedTransitions} as properties).
 *
 * <p>Mapped to <strong>409 Conflict</strong> by the {@code ProblemDetailExceptionHandler}.
 */
public non-sealed class InvalidStateException extends DomainException {

  public InvalidStateException(String type, String title, String detail) {
    super(type, title, detail);
  }

  public InvalidStateException(
      String type, String title, String detail, Map<String, Object> properties) {
    super(type, title, detail, properties);
  }

  public InvalidStateException(String type, String title, String detail, Throwable cause) {
    super(type, title, detail, cause);
  }

  public InvalidStateException(
      String type, String title, String detail, Map<String, Object> properties, Throwable cause) {
    super(type, title, detail, properties, cause);
  }
}
