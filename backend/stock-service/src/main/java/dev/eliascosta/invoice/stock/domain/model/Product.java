package dev.eliascosta.invoice.stock.domain.model;

import static dev.eliascosta.invoice.stock.domain.model.DomainValidation.requiredText;

import dev.eliascosta.invoice.stock.domain.exception.InsufficientStockException;
import dev.eliascosta.invoice.stock.domain.exception.ProductInactiveException;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class Product {

  private final UUID id;
  private final ProductCode code;
  private final long version;
  private final String createdBy;
  private final Instant createdAt;
  private String description;
  private Money unitPrice;
  private Quantity balance;
  private boolean active;
  private String updatedBy;
  private Instant updatedAt;

  /** Restores a persisted product. New products must use {@link #create}. */
  public Product(
      UUID id,
      ProductCode code,
      String description,
      Money unitPrice,
      Quantity balance,
      boolean active,
      long version,
      String createdBy,
      Instant createdAt,
      String updatedBy,
      Instant updatedAt) {
    this.id = Objects.requireNonNull(id, "id");
    this.code = Objects.requireNonNull(code, "code");
    this.description = requiredText(description, "description", 200);
    this.unitPrice = Objects.requireNonNull(unitPrice, "unitPrice");
    this.balance = Objects.requireNonNull(balance, "balance");
    this.active = active;
    this.version = version;
    this.createdBy = requiredText(createdBy, "createdBy", 100);
    this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
    this.updatedBy = updatedBy == null ? null : requiredText(updatedBy, "updatedBy", 100);
    this.updatedAt = updatedAt;
  }

  public static Creation create(
      ProductCode code,
      String description,
      Money unitPrice,
      Quantity initialBalance,
      String createdBy,
      Instant createdAt) {
    Product product =
        new Product(
            UUID.randomUUID(),
            code,
            description,
            unitPrice,
            initialBalance,
            true,
            0,
            createdBy,
            createdAt,
            null,
            null);
    StockMovement initialMovement =
        StockMovement.create(
            product.id,
            MovementType.INITIAL,
            initialBalance,
            initialBalance,
            null,
            null,
            null,
            createdBy,
            createdAt);
    return new Creation(product, initialMovement);
  }

  public record Creation(Product product, StockMovement initialMovement) {}

  public StockMovement credit(
      Quantity quantity, String reason, String createdBy, Instant createdAt) {
    quantity.requirePositive();
    return applyMovement(
        MovementType.INBOUND,
        quantity,
        balance.plus(quantity),
        reason,
        null,
        null,
        createdBy,
        createdAt);
  }

  public StockMovement debitManual(
      Quantity quantity, String reason, String createdBy, Instant createdAt) {
    return applyMovement(
        MovementType.OUTBOUND,
        quantity,
        balanceAfterDebit(quantity),
        reason,
        null,
        null,
        createdBy,
        createdAt);
  }

  public StockMovement debitForInvoice(
      Quantity quantity,
      UUID invoiceId,
      long invoiceNumber,
      String requestedBy,
      Instant createdAt) {
    return applyMovement(
        MovementType.INVOICE_DEBIT,
        quantity,
        balanceAfterDebit(quantity),
        null,
        invoiceId,
        invoiceNumber,
        requestedBy,
        createdAt);
  }

  private Quantity balanceAfterDebit(Quantity quantity) {
    ensureActive();
    quantity.requirePositive();
    if (balance.isLessThan(quantity)) {
      throw new InsufficientStockException(code, quantity, balance);
    }
    return balance.minus(quantity);
  }

  private StockMovement applyMovement(
      MovementType type,
      Quantity quantity,
      Quantity balanceAfter,
      String reason,
      UUID referenceId,
      Long referenceNumber,
      String createdBy,
      Instant createdAt) {
    StockMovement movement =
        StockMovement.create(
            id,
            type,
            quantity,
            balanceAfter,
            reason,
            referenceId,
            referenceNumber,
            createdBy,
            createdAt);
    balance = movement.getBalanceAfter();
    updatedBy = movement.getCreatedBy();
    updatedAt = movement.getCreatedAt();
    return movement;
  }

  public void ensureActive() {
    if (!active) {
      throw new ProductInactiveException(id);
    }
  }

  public void deactivate(String updatedBy, Instant updatedAt) {
    markUpdated(updatedBy, updatedAt);
    active = false;
  }

  public void reactivate(String updatedBy, Instant updatedAt) {
    markUpdated(updatedBy, updatedAt);
    active = true;
  }

  public void changeDescription(String description, String updatedBy, Instant updatedAt) {
    String validatedDescription = requiredText(description, "description", 200);
    markUpdated(updatedBy, updatedAt);
    this.description = validatedDescription;
  }

  public void changePrice(Money unitPrice, String updatedBy, Instant updatedAt) {
    Objects.requireNonNull(unitPrice, "unitPrice");
    markUpdated(updatedBy, updatedAt);
    this.unitPrice = unitPrice;
  }

  private void markUpdated(String updatedBy, Instant updatedAt) {
    Objects.requireNonNull(updatedAt, "updatedAt");
    this.updatedBy = requiredText(updatedBy, "updatedBy", 100);
    this.updatedAt = updatedAt;
  }

  public UUID getId() {
    return id;
  }

  public ProductCode getCode() {
    return code;
  }

  public String getDescription() {
    return description;
  }

  public Money getUnitPrice() {
    return unitPrice;
  }

  public Quantity getBalance() {
    return balance;
  }

  public boolean isActive() {
    return active;
  }

  public long getVersion() {
    return version;
  }

  public String getCreatedBy() {
    return createdBy;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public String getUpdatedBy() {
    return updatedBy;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
