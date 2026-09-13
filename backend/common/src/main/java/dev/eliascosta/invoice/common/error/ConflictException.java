package dev.eliascosta.invoice.common.error;

import java.util.Map;

/**
 * Request conflicts with the current state of a resource: uniqueness ({@code
 * product-code-already-exists}) or a key still being processed ({@code idempotency-key-in-flight}).
 *
 * <p>Mapped to <strong>409 Conflict</strong> by the {@code ProblemDetailExceptionHandler}.
 */
public non-sealed class ConflictException extends DomainException {

  public ConflictException(String type, String title, String detail) {
    super(type, title, detail);
  }

  public ConflictException(
      String type, String title, String detail, Map<String, Object> properties) {
    super(type, title, detail, properties);
  }

  public ConflictException(String type, String title, String detail, Throwable cause) {
    super(type, title, detail, cause);
  }

  public ConflictException(
      String type, String title, String detail, Map<String, Object> properties, Throwable cause) {
    super(type, title, detail, properties, cause);
  }
}
