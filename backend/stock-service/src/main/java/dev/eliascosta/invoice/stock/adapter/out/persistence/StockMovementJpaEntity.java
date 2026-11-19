package dev.eliascosta.invoice.stock.adapter.out.persistence;

import dev.eliascosta.invoice.stock.domain.model.MovementType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.Immutable;

@Entity
@Immutable
@Table(name = "stock_movements")
public class StockMovementJpaEntity {

  @Id private UUID id;

  @Column(name = "product_id", nullable = false)
  private UUID productId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private MovementType type;

  @Column(nullable = false)
  private int quantity;

  @Column(name = "balance_after", nullable = false)
  private int balanceAfter;

  @Column(length = 200)
  private String reason;

  @Column(name = "reference_id")
  private UUID referenceId;

  @Column(name = "reference_number")
  private Long referenceNumber;

  @Column(name = "created_by", nullable = false, length = 100)
  private String createdBy;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  protected StockMovementJpaEntity() {}

  public StockMovementJpaEntity(
      UUID id,
      UUID productId,
      MovementType type,
      int quantity,
      int balanceAfter,
      String reason,
      UUID referenceId,
      Long referenceNumber,
      String createdBy,
      Instant createdAt) {
    this.id = id;
    this.productId = productId;
    this.type = type;
    this.quantity = quantity;
    this.balanceAfter = balanceAfter;
    this.reason = reason;
    this.referenceId = referenceId;
    this.referenceNumber = referenceNumber;
    this.createdBy = createdBy;
    this.createdAt = createdAt;
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

  public int getQuantity() {
    return quantity;
  }

  public int getBalanceAfter() {
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
