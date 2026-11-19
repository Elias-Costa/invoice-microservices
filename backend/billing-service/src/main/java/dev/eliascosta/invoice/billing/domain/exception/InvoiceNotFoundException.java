package dev.eliascosta.invoice.billing.domain.exception;

import dev.eliascosta.invoice.common.error.NotFoundException;
import java.util.UUID;

public final class InvoiceNotFoundException extends NotFoundException {

  public InvoiceNotFoundException(UUID id) {
    super("invoice-not-found", "Nota não encontrada", "Nota %s não encontrada.".formatted(id));
  }
}
