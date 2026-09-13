package dev.eliascosta.invoice.common.error;

import java.util.Map;

/**
 * Request is semantically invalid for reasons Bean Validation cannot express (e.g. {@code
 * field-immutable}, {@code duplicate-invoice-item}). See D-009.
 *
 * <p>Mapped to <strong>400 Bad Request</strong> by the {@code ProblemDetailExceptionHandler}.
 */
public non-sealed class InvalidRequestException extends DomainException {

  public InvalidRequestException(String type, String title, String detail) {
    super(type, title, detail);
  }

  public InvalidRequestException(
      String type, String title, String detail, Map<String, Object> properties) {
    super(type, title, detail, properties);
  }

  public InvalidRequestException(String type, String title, String detail, Throwable cause) {
    super(type, title, detail, cause);
  }

  public InvalidRequestException(
      String type, String title, String detail, Map<String, Object> properties, Throwable cause) {
    super(type, title, detail, properties, cause);
  }
}
