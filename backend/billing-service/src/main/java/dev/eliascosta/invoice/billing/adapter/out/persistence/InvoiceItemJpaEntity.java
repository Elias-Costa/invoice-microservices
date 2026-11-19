package dev.eliascosta.invoice.billing.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;
import org.hibernate.annotations.Immutable;

@Entity
@Immutable
@Table(name = "invoice_items")
public class InvoiceItemJpaEntity {

  @Id private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "invoice_id", nullable = false, updatable = false)
  private InvoiceJpaEntity invoice;

  @Column(name = "product_id", nullable = false)
  private UUID productId;

  @Column(name = "product_code", nullable = false, length = 30)
  private String productCode;

  @Column(name = "product_description", nullable = false, length = 200)
  private String productDescription;

  @Column(name = "unit_price", nullable = false, precision = 15, scale = 2)
  private BigDecimal unitPrice;

  @Column(nullable = false)
  private int quantity;

  @Column(name = "line_total", nullable = false, precision = 15, scale = 2)
  private BigDecimal lineTotal;

  @Column(name = "line_order", nullable = false)
  private int lineOrder;

  protected InvoiceItemJpaEntity() {}

  public InvoiceItemJpaEntity(
      UUID id,
      UUID productId,
      String productCode,
      String productDescription,
      BigDecimal unitPrice,
      int quantity,
      BigDecimal lineTotal,
      int lineOrder) {
    this.id = id;
    this.productId = productId;
    this.productCode = productCode;
    this.productDescription = productDescription;
    this.unitPrice = unitPrice;
    this.quantity = quantity;
    this.lineTotal = lineTotal;
    this.lineOrder = lineOrder;
  }

  void attachTo(InvoiceJpaEntity invoice) {
    this.invoice = invoice;
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

  public BigDecimal getUnitPrice() {
    return unitPrice;
  }

  public int getQuantity() {
    return quantity;
  }

  public int getLineOrder() {
    return lineOrder;
  }
}
