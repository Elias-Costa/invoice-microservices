package dev.eliascosta.invoice.stock.domain.exception;

import dev.eliascosta.invoice.common.error.BusinessRuleException;
import dev.eliascosta.invoice.stock.domain.model.ProductCode;
import dev.eliascosta.invoice.stock.domain.model.Quantity;

public final class InsufficientStockException extends BusinessRuleException {

  public InsufficientStockException(ProductCode code, Quantity requested, Quantity available) {
    super(
        "insufficient-stock",
        "Saldo insuficiente",
        "Saldo insuficiente para %s: solicitado %d, disponível %d."
            .formatted(code.value(), requested.value(), available.value()));
  }
}
