package dev.eliascosta.invoice.stock.domain.model;

import dev.eliascosta.invoice.common.error.InvalidRequestException;
import dev.eliascosta.invoice.common.error.ProblemTypes;
import java.util.List;
import java.util.Map;

final class DomainValidation {

  private DomainValidation() {}

  static InvalidRequestException invalid(String field, String message) {
    return new InvalidRequestException(
        ProblemTypes.VALIDATION_ERROR,
        "Dados inválidos",
        message,
        Map.of("errors", List.of(Map.of("field", field, "message", message))));
  }

  static String requiredText(String value, String field, int maxLength) {
    if (value == null || value.isBlank()) {
      throw invalid(field, "O campo é obrigatório.");
    }
    if (value.codePointCount(0, value.length()) > maxLength) {
      throw invalid(field, "O campo deve ter no máximo %d caracteres.".formatted(maxLength));
    }
    return value;
  }
}
