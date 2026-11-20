package dev.eliascosta.invoice.billing.adapter.in.rest;

import dev.eliascosta.invoice.billing.domain.model.InvoiceStatus;
import java.util.UUID;

record PrintInvoiceResponse(UUID id, InvoiceStatus status) {}
