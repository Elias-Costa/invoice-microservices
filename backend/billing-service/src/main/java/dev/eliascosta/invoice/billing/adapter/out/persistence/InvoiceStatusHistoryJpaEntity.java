package dev.eliascosta.invoice.billing.adapter.out.persistence;

import dev.eliascosta.invoice.billing.domain.model.InvoiceStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.Immutable;

@Entity
@Immutable
@Table(name = "invoice_status_history")
public class InvoiceStatusHistoryJpaEntity {

  @Id private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "invoice_id", nullable = false, updatable = false)
  private InvoiceJpaEntity invoice;

  @Enumerated(EnumType.STRING)
  @Column(name = "from_status", length = 20)
  private InvoiceStatus fromStatus;

  @Enumerated(EnumType.STRING)
  @Column(name = "to_status", length = 20, nullable = false)
  private InvoiceStatus toStatus;

  @Column(length = 500)
  private String reason;

  @Column(name = "changed_by", nullable = false, length = 100)
  private String changedBy;

  @Column(name = "changed_at", nullable = false)
  private Instant changedAt;

  protected InvoiceStatusHistoryJpaEntity() {}

  public InvoiceStatusHistoryJpaEntity(
      UUID id,
      InvoiceStatus fromStatus,
      InvoiceStatus toStatus,
      String reason,
      String changedBy,
      Instant changedAt) {
    this.id = id;
    this.fromStatus = fromStatus;
    this.toStatus = toStatus;
    this.reason = reason;
    this.changedBy = changedBy;
    this.changedAt = changedAt;
  }

  void attachTo(InvoiceJpaEntity invoice) {
    this.invoice = invoice;
  }

  public UUID getId() {
    return id;
  }

  public InvoiceStatus getFromStatus() {
    return fromStatus;
  }

  public InvoiceStatus getToStatus() {
    return toStatus;
  }

  public String getReason() {
    return reason;
  }

  public String getChangedBy() {
    return changedBy;
  }

  public Instant getChangedAt() {
    return changedAt;
  }
}
