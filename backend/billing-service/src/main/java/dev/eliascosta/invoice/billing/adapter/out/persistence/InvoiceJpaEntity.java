package dev.eliascosta.invoice.billing.adapter.out.persistence;

import dev.eliascosta.invoice.billing.domain.model.InvoiceStatus;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.Generated;
import org.hibernate.annotations.OptimisticLock;

@Entity
@Table(name = "invoices")
public class InvoiceJpaEntity {

  @Id private UUID id;

  @Generated
  @Column(nullable = false, insertable = false, updatable = false)
  private Long number;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private InvoiceStatus status;

  @Column(name = "customer_id", nullable = false, updatable = false)
  private UUID customerId;

  @Column(name = "customer_name", nullable = false, length = 150, updatable = false)
  private String customerName;

  @Column(name = "customer_document", nullable = false, length = 14, updatable = false)
  private String customerDocument;

  @Column(nullable = false, precision = 15, scale = 2)
  private BigDecimal total;

  @Column(name = "current_print_request_id")
  private UUID printRequestId;

  @Column(name = "processing_started_at")
  private Instant processingStartedAt;

  @Column(name = "last_failure_reason", length = 500)
  private String lastFailureReason;

  @Column(name = "cancel_reason", length = 500)
  private String cancelReason;

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

  @Column(name = "closed_at")
  private Instant closedAt;

  @Column(name = "canceled_at")
  private Instant canceledAt;

  @OneToMany(mappedBy = "invoice", cascade = CascadeType.ALL, orphanRemoval = true)
  @OrderBy("lineOrder ASC")
  @OptimisticLock(excluded = false)
  private List<InvoiceItemJpaEntity> items = new ArrayList<>();

  @OneToMany(mappedBy = "invoice", cascade = CascadeType.ALL)
  @OrderBy("changedAt ASC, id ASC")
  @OptimisticLock(excluded = false)
  private List<InvoiceStatusHistoryJpaEntity> history = new ArrayList<>();

  protected InvoiceJpaEntity() {}

  public InvoiceJpaEntity(
      UUID id,
      UUID customerId,
      String customerName,
      String customerDocument,
      String createdBy,
      Instant createdAt) {
    this.id = id;
    this.customerId = customerId;
    this.customerName = customerName;
    this.customerDocument = customerDocument;
    this.createdBy = createdBy;
    this.createdAt = createdAt;
  }

  void addItem(InvoiceItemJpaEntity item) {
    item.attachTo(this);
    items.add(item);
  }

  void addHistory(InvoiceStatusHistoryJpaEntity entry) {
    entry.attachTo(this);
    history.add(entry);
  }

  public UUID getId() {
    return id;
  }

  public Long getNumber() {
    return number;
  }

  public InvoiceStatus getStatus() {
    return status;
  }

  public void setStatus(InvoiceStatus status) {
    this.status = status;
  }

  public UUID getCustomerId() {
    return customerId;
  }

  public String getCustomerName() {
    return customerName;
  }

  public String getCustomerDocument() {
    return customerDocument;
  }

  public BigDecimal getTotal() {
    return total;
  }

  public void setTotal(BigDecimal total) {
    this.total = total;
  }

  public UUID getPrintRequestId() {
    return printRequestId;
  }

  public void setPrintRequestId(UUID printRequestId) {
    this.printRequestId = printRequestId;
  }

  public Instant getProcessingStartedAt() {
    return processingStartedAt;
  }

  public void setProcessingStartedAt(Instant processingStartedAt) {
    this.processingStartedAt = processingStartedAt;
  }

  public String getLastFailureReason() {
    return lastFailureReason;
  }

  public void setLastFailureReason(String lastFailureReason) {
    this.lastFailureReason = lastFailureReason;
  }

  public String getCancelReason() {
    return cancelReason;
  }

  public void setCancelReason(String cancelReason) {
    this.cancelReason = cancelReason;
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

  public Instant getClosedAt() {
    return closedAt;
  }

  public void setClosedAt(Instant closedAt) {
    this.closedAt = closedAt;
  }

  public Instant getCanceledAt() {
    return canceledAt;
  }

  public void setCanceledAt(Instant canceledAt) {
    this.canceledAt = canceledAt;
  }

  public List<InvoiceItemJpaEntity> getItems() {
    return items;
  }

  public List<InvoiceStatusHistoryJpaEntity> getHistory() {
    return history;
  }
}
