package dev.eliascosta.invoice.billing.domain.exception;

import dev.eliascosta.invoice.common.error.BusinessRuleException;
import java.util.Map;
import java.util.UUID;

public final class InvalidInvoiceReferenceException extends BusinessRuleException {

  public static InvalidInvoiceReferenceException customerNotFound(UUID id) {
    return new InvalidInvoiceReferenceException(
        "customer-not-found",
        "Cliente não encontrado",
        "Cliente %s não encontrado.".formatted(id),
        Map.of("customerId", id));
  }

  public static InvalidInvoiceReferenceException productNotFound(UUID id) {
    return new InvalidInvoiceReferenceException(
        "product-not-found",
        "Produto não encontrado",
        "Produto %s não encontrado.".formatted(id),
        Map.of("productId", id));
  }

  public static InvalidInvoiceReferenceException productInactive(UUID id) {
    return new InvalidInvoiceReferenceException(
        "product-inactive",
        "Produto inativo",
        "O produto %s está inativo.".formatted(id),
        Map.of("productId", id));
  }

  private InvalidInvoiceReferenceException(
      String type, String title, String detail, Map<String, Object> properties) {
    super(type, title, detail, properties);
  }
}
