package dev.eliascosta.invoice.billing.domain.exception;

import dev.eliascosta.invoice.common.error.BusinessRuleException;
import java.util.Map;
import java.util.UUID;

public final class CustomerInactiveException extends BusinessRuleException {

  public CustomerInactiveException(UUID customerId) {
    super(
        "customer-inactive",
        "Cliente inativo",
        "O cliente %s está inativo.".formatted(customerId),
        Map.of("customerId", customerId));
  }
}
