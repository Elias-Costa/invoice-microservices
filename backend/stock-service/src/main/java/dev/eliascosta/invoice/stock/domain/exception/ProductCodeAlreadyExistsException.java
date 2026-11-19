package dev.eliascosta.invoice.stock.domain.exception;

import dev.eliascosta.invoice.common.error.ConflictException;
import dev.eliascosta.invoice.stock.domain.model.ProductCode;

public final class ProductCodeAlreadyExistsException extends ConflictException {

  public ProductCodeAlreadyExistsException(ProductCode code) {
    super(
        "product-code-already-exists",
        "Código de produto já cadastrado",
        "Já existe um produto com o código %s.".formatted(code.value()));
  }
}
