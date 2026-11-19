package dev.eliascosta.invoice.stock.domain.exception;

import dev.eliascosta.invoice.common.error.NotFoundException;
import java.util.UUID;

public final class ProductNotFoundException extends NotFoundException {

  public ProductNotFoundException(UUID productId) {
    super(
        "product-not-found",
        "Produto não encontrado",
        "Produto %s não encontrado.".formatted(productId));
  }
}
