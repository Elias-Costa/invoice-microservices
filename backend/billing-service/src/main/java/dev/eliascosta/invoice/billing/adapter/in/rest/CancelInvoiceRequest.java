package dev.eliascosta.invoice.billing.adapter.in.rest;

import jakarta.validation.constraints.NotBlank;
import org.hibernate.validator.constraints.CodePointLength;

record CancelInvoiceRequest(@NotBlank @CodePointLength(max = 500) String reason) {}
