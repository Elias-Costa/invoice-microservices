package dev.eliascosta.invoice.billing.adapter.in.rest;

import dev.eliascosta.invoice.billing.domain.model.InvoiceStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

record InvoiceResponse(
    UUID id,
    long number,
    InvoiceStatus status,
    CustomerSnapshot customer,
    List<Item> items,
    BigDecimal total,
    Instant createdAt,
    Instant closedAt,
    Instant canceledAt,
    String lastFailureReason,
    String cancelReason,
    long version,
    List<Action> allowedActions) {

  record CustomerSnapshot(UUID id, String name, String document) {}

  record Item(
      UUID id,
      UUID productId,
      String productCode,
      String productDescription,
      BigDecimal unitPrice,
      int quantity,
      BigDecimal lineTotal,
      int lineOrder) {}

  enum Action {
    EDIT_ITEMS,
    CANCEL,
    PRINT
  }
}
