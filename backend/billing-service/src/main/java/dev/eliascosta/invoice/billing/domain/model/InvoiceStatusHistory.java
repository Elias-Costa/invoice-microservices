package dev.eliascosta.invoice.billing.domain.model;

import static dev.eliascosta.invoice.billing.domain.model.DomainValidation.requiredText;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class InvoiceStatusHistory {

  private final UUID id;
  private final InvoiceStatus fromStatus;
  private final InvoiceStatus toStatus;
  private final String reason;
  private final String changedBy;
  private final Instant changedAt;

  public InvoiceStatusHistory(
      UUID id,
      InvoiceStatus fromStatus,
      InvoiceStatus toStatus,
      String reason,
      String changedBy,
      Instant changedAt) {
    this.id = Objects.requireNonNull(id, "id");
    this.fromStatus = fromStatus;
    this.toStatus = Objects.requireNonNull(toStatus, "toStatus");
    this.reason = reason == null ? null : requiredText(reason, "reason", 500);
    this.changedBy = requiredText(changedBy, "changedBy", 100);
    this.changedAt = Objects.requireNonNull(changedAt, "changedAt");
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
