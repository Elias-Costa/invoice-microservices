package dev.eliascosta.invoice.stock.domain.model;

import static dev.eliascosta.invoice.stock.domain.model.DomainValidation.invalid;
import static dev.eliascosta.invoice.stock.domain.model.DomainValidation.requiredText;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class StockMovement {

  private final UUID id;
  private final UUID productId;
  private final MovementType type;
  private final Quantity quantity;
  private final Quantity balanceAfter;
  private final String reason;
  private final UUID referenceId;
  private final Long referenceNumber;
  private final String createdBy;
  private final Instant createdAt;

  /** Restores a persisted movement. New movements are issued by {@link Product}. */
  public StockMovement(
      UUID id,
      UUID productId,
      MovementType type,
      Quantity quantity,
      Quantity balanceAfter,
      String reason,
      UUID referenceId,
      Long referenceNumber,
      String createdBy,
      Instant createdAt) {
    this.id = Objects.requireNonNull(id, "id");
    this.productId = Objects.requireNonNull(productId, "productId");
    this.type = Objects.requireNonNull(type, "type");
    this.quantity = Objects.requireNonNull(quantity, "quantity");
    this.balanceAfter = Objects.requireNonNull(balanceAfter, "balanceAfter");
    if (type == MovementType.INITIAL) {
      if (!quantity.equals(balanceAfter)) {
        throw invalid(
            "balanceAfter", "O saldo após a movimentação inicial deve ser igual à quantidade.");
      }
    } else {
      quantity.requirePositive();
    }

    boolean manual = type == MovementType.INBOUND || type == MovementType.OUTBOUND;
    this.reason = manual || reason != null ? requiredText(reason, "reason", 200) : null;
    validateInvoiceReference(type, referenceId, referenceNumber);
    this.referenceId = referenceId;
    this.referenceNumber = referenceNumber;
    this.createdBy = requiredText(createdBy, "createdBy", 100);
    this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
  }

  static StockMovement create(
      UUID productId,
      MovementType type,
      Quantity quantity,
      Quantity balanceAfter,
      String reason,
      UUID referenceId,
      Long referenceNumber,
      String createdBy,
      Instant createdAt) {
    return new StockMovement(
        UUID.randomUUID(),
        productId,
        type,
        quantity,
        balanceAfter,
        reason,
        referenceId,
        referenceNumber,
        createdBy,
        createdAt);
  }

  private static void validateInvoiceReference(
      MovementType type, UUID referenceId, Long referenceNumber) {
    if (type == MovementType.INVOICE_DEBIT) {
      if (referenceId == null || referenceNumber == null) {
        throw invalid("referenceId", "A baixa por nota exige o identificador e o número da nota.");
      }
    } else if (referenceId != null || referenceNumber != null) {
      throw invalid("referenceId", "A referência de nota só se aplica à baixa por nota.");
    }
  }

  public UUID getId() {
    return id;
  }

  public UUID getProductId() {
    return productId;
  }

  public MovementType getType() {
    return type;
  }

  public Quantity getQuantity() {
    return quantity;
  }

  public Quantity getBalanceAfter() {
    return balanceAfter;
  }

  public String getReason() {
    return reason;
  }

  public UUID getReferenceId() {
    return referenceId;
  }

  public Long getReferenceNumber() {
    return referenceNumber;
  }

  public String getCreatedBy() {
    return createdBy;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
