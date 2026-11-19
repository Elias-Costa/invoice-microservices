package dev.eliascosta.invoice.billing.domain.model;

import java.util.List;

public enum InvoiceStatus {
  OPEN,
  PROCESSING,
  CLOSED,
  CANCELED;

  public List<InvoiceStatus> allowedTransitions() {
    return switch (this) {
      case OPEN -> List.of(OPEN, PROCESSING, CANCELED);
      case PROCESSING -> List.of(CLOSED, OPEN);
      case CLOSED, CANCELED -> List.of();
    };
  }
}
