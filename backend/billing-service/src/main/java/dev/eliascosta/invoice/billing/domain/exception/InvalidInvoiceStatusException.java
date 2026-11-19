package dev.eliascosta.invoice.billing.domain.exception;

import dev.eliascosta.invoice.billing.domain.model.InvoiceStatus;
import dev.eliascosta.invoice.common.error.InvalidStateException;
import java.util.Map;

public final class InvalidInvoiceStatusException extends InvalidStateException {

  public InvalidInvoiceStatusException(InvoiceStatus currentStatus) {
    super(
        "invalid-invoice-status",
        "Status da nota inválido",
        "A operação não é permitida para uma nota no status %s.".formatted(currentStatus),
        Map.of(
            "currentStatus", currentStatus.name(),
            "allowedTransitions",
                currentStatus.allowedTransitions().stream().map(Enum::name).toList()));
  }
}
