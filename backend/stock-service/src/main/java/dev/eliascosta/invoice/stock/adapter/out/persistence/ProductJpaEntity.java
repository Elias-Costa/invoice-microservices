package dev.eliascosta.invoice.stock.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "products")
public class ProductJpaEntity {

  @Id private UUID id;

  @Column(nullable = false, length = 30, updatable = false)
  private String code;

  @Column(nullable = false, length = 200)
  private String description;

  @Column(name = "unit_price", nullable = false, precision = 15, scale = 2)
  private BigDecimal unitPrice;

  @Column(nullable = false)
  private int balance;

  @Column(nullable = false)
  private boolean active;

  @Version
  @Column(nullable = false)
  private long version;

  @Column(name = "created_by", nullable = false, length = 100, updatable = false)
  private String createdBy;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_by", length = 100)
  private String updatedBy;

  @Column(name = "updated_at")
  private Instant updatedAt;

  protected ProductJpaEntity() {}

  public ProductJpaEntity(
      UUID id,
      String code,
      String description,
      BigDecimal unitPrice,
      int balance,
      boolean active,
      long version,
      String createdBy,
      Instant createdAt,
      String updatedBy,
      Instant updatedAt) {
    this.id = id;
    this.code = code;
    this.description = description;
    this.unitPrice = unitPrice;
    this.balance = balance;
    this.active = active;
    this.version = version;
    this.createdBy = createdBy;
    this.createdAt = createdAt;
    this.updatedBy = updatedBy;
    this.updatedAt = updatedAt;
  }

  public UUID getId() {
    return id;
  }

  public String getCode() {
    return code;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public BigDecimal getUnitPrice() {
    return unitPrice;
  }

  public void setUnitPrice(BigDecimal unitPrice) {
    this.unitPrice = unitPrice;
  }

  public int getBalance() {
    return balance;
  }

  public void setBalance(int balance) {
    this.balance = balance;
  }

  public boolean isActive() {
    return active;
  }

  public void setActive(boolean active) {
    this.active = active;
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

  public void setUpdatedBy(String updatedBy) {
    this.updatedBy = updatedBy;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(Instant updatedAt) {
    this.updatedAt = updatedAt;
  }
}
