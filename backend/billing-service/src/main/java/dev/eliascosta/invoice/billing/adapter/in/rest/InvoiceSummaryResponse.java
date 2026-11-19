package dev.eliascosta.invoice.billing.adapter.in.rest;

import dev.eliascosta.invoice.billing.domain.model.InvoiceStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

record InvoiceSummaryResponse(
    UUID id,
    long number,
    InvoiceStatus status,
    UUID customerId,
    String customerName,
    BigDecimal total,
    Instant createdAt) {}
