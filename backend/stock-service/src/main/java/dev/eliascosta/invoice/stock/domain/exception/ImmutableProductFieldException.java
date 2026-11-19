package dev.eliascosta.invoice.stock.domain.exception;

import dev.eliascosta.invoice.common.error.InvalidRequestException;
import java.util.List;
import java.util.Map;

public final class ImmutableProductFieldException extends InvalidRequestException {

  public ImmutableProductFieldException(String field, String detail) {
    super(
        "field-immutable",
        "Campo imutável",
        detail,
        Map.of("errors", List.of(Map.of("field", field, "message", detail))));
  }
}
