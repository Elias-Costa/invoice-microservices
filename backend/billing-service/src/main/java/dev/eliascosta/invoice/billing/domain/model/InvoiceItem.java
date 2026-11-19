package dev.eliascosta.invoice.billing.domain.model;

import static dev.eliascosta.invoice.billing.domain.model.DomainValidation.requiredText;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

public final class InvoiceItem {

  private final UUID id;
  private final UUID productId;
  private final String productCode;
  private final String productDescription;
  private final Money unitPrice;
  private final Quantity quantity;
  private final Money lineTotal;
  private final int lineOrder;

  public InvoiceItem(
      UUID id,
      UUID productId,
      String productCode,
      String productDescription,
      Money unitPrice,
      Quantity quantity,
      int lineOrder) {
    this.id = Objects.requireNonNull(id, "id");
    this.productId = Objects.requireNonNull(productId, "productId");
    this.productCode = requiredText(productCode, "productCode", 30);
    this.productDescription = requiredText(productDescription, "productDescription", 200);
    this.unitPrice = Objects.requireNonNull(unitPrice, "unitPrice");
    this.quantity = Objects.requireNonNull(quantity, "quantity");
    if (lineOrder < 1) {
      throw new IllegalArgumentException("lineOrder must be positive");
    }
    this.lineOrder = lineOrder;
    this.lineTotal =
        Money.calculatedTotal(
            unitPrice.value().multiply(BigDecimal.valueOf(quantity.value())), "lineTotal");
  }

  static InvoiceItem create(Input input, int lineOrder) {
    return new InvoiceItem(
        UUID.randomUUID(),
        input.productId(),
        input.productCode(),
        input.productDescription(),
        input.unitPrice(),
        input.quantity(),
        lineOrder);
  }

  public UUID getId() {
    return id;
  }

  public UUID getProductId() {
    return productId;
  }

  public String getProductCode() {
    return productCode;
  }

  public String getProductDescription() {
    return productDescription;
  }

  public Money getUnitPrice() {
    return unitPrice;
  }

  public Quantity getQuantity() {
    return quantity;
  }

  public Money getLineTotal() {
    return lineTotal;
  }

  public int getLineOrder() {
    return lineOrder;
  }

  public record Input(
      UUID productId,
      String productCode,
      String productDescription,
      Money unitPrice,
      Quantity quantity) {}
}
