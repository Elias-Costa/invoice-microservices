package dev.eliascosta.invoice.billing.adapter.in.rest;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import dev.eliascosta.invoice.billing.domain.model.InvoiceStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

record CustomerDetailResponse(
    @JsonUnwrapped CustomerResponse customer, List<RecentInvoice> recentInvoices) {

  record RecentInvoice(
      UUID id, Long number, InvoiceStatus status, BigDecimal total, Instant createdAt) {}
}
