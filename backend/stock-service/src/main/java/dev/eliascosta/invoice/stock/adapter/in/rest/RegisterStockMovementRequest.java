package dev.eliascosta.invoice.stock.adapter.in.rest;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

record RegisterStockMovementRequest(
    @NotNull @Pattern(regexp = "INBOUND|OUTBOUND", message = "Use INBOUND ou OUTBOUND.") String type,
    @NotNull @Positive Integer quantity,
    @NotBlank String reason) {}
