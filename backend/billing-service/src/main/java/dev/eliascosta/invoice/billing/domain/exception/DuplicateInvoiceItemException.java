package dev.eliascosta.invoice.billing.domain.exception;

import dev.eliascosta.invoice.common.error.InvalidRequestException;
import java.util.UUID;

public final class DuplicateInvoiceItemException extends InvalidRequestException {

  public DuplicateInvoiceItemException(UUID productId) {
    super(
        "duplicate-invoice-item",
        "Produto repetido",
        "O produto %s aparece mais de uma vez na nota.".formatted(productId));
  }
}
