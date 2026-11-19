package dev.eliascosta.invoice.billing.domain.exception;

import dev.eliascosta.invoice.common.error.NotFoundException;
import java.util.UUID;

public final class CustomerNotFoundException extends NotFoundException {

  public CustomerNotFoundException(UUID id) {
    super(
        "customer-not-found", "Cliente não encontrado", "Cliente %s não encontrado.".formatted(id));
  }
}
