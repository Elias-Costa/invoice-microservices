package dev.eliascosta.invoice.stock.domain.exception;

import dev.eliascosta.invoice.common.error.BusinessRuleException;
import java.util.UUID;

public final class ProductInactiveException extends BusinessRuleException {

  public ProductInactiveException(UUID productId) {
    super("product-inactive", "Produto inativo", "O produto %s está inativo.".formatted(productId));
  }
}
