package dev.eliascosta.invoice.stock.adapter.out.persistence;

import dev.eliascosta.invoice.stock.domain.model.InvoiceDebit.Outcome;
import dev.eliascosta.invoice.stock.domain.model.InvoiceDebit.RejectionReason;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Immutable
@Table(name = "invoice_debits")
class InvoiceDebitJpaEntity {

  @Id
  @Column(name = "print_request_id", nullable = false)
  private UUID printRequestId;

  @Column(name = "invoice_id", nullable = false)
  private UUID invoiceId;

  @Column(name = "invoice_number", nullable = false)
  private long invoiceNumber;

  @Column(name = "request_event_id", nullable = false)
  private UUID requestEventId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private Outcome outcome;

  @Enumerated(EnumType.STRING)
  @Column(length = 30)
  private RejectionReason reason;

  @JdbcTypeCode(SqlTypes.JSON)
  private String details;

  @Column(name = "processed_at", nullable = false)
  private Instant processedAt;

  protected InvoiceDebitJpaEntity() {}

  public InvoiceDebitJpaEntity(
      UUID printRequestId,
      UUID invoiceId,
      long invoiceNumber,
      UUID requestEventId,
      Outcome outcome,
      RejectionReason reason,
      String details,
      Instant processedAt) {
    this.printRequestId = printRequestId;
    this.invoiceId = invoiceId;
    this.invoiceNumber = invoiceNumber;
    this.requestEventId = requestEventId;
    this.outcome = outcome;
    this.reason = reason;
    this.details = details;
    this.processedAt = processedAt;
  }

  public UUID getPrintRequestId() {
    return printRequestId;
  }

  public UUID getInvoiceId() {
    return invoiceId;
  }

  public long getInvoiceNumber() {
    return invoiceNumber;
  }

  public UUID getRequestEventId() {
    return requestEventId;
  }

  public Outcome getOutcome() {
    return outcome;
  }

  public RejectionReason getReason() {
    return reason;
  }

  public String getDetails() {
    return details;
  }

  public Instant getProcessedAt() {
    return processedAt;
  }
}
