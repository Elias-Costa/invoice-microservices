package dev.eliascosta.invoice.billing.adapter.in.rest;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.util.UUID;

record InvoiceItemRequest(
    @NotNull UUID productId,
    @NotNull @Positive @Digits(integer = 10, fraction = 0) @Max(Integer.MAX_VALUE) BigDecimal quantity) {}
