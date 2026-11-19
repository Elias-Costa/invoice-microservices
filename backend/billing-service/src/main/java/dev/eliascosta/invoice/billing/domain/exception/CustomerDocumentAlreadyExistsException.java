package dev.eliascosta.invoice.billing.domain.exception;

import dev.eliascosta.invoice.common.error.ConflictException;

public final class CustomerDocumentAlreadyExistsException extends ConflictException {

  public CustomerDocumentAlreadyExistsException() {
    super(
        "customer-document-already-exists",
        "Documento já cadastrado",
        "Já existe um cliente com o CPF ou CNPJ informado.");
  }
}
