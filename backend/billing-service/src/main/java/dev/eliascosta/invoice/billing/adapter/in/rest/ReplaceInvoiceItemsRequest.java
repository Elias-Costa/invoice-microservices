package dev.eliascosta.invoice.billing.adapter.in.rest;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import java.util.List;
import tools.jackson.databind.JsonNode;

record ReplaceInvoiceItemsRequest(
    @NotEmpty List<@NotNull @Valid InvoiceItemRequest> items,
    @NotNull @PositiveOrZero @Digits(integer = 19, fraction = 0) @Max(Long.MAX_VALUE) BigDecimal version,
    JsonNode customerId) {}
