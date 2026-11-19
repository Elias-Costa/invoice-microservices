package dev.eliascosta.invoice.stock.adapter.in.rest;

import dev.eliascosta.invoice.stock.domain.model.MovementType;
import java.time.Instant;
import java.util.UUID;

record StockMovementResponse(
    UUID id,
    UUID productId,
    MovementType type,
    int quantity,
    int balanceAfter,
    String reason,
    UUID referenceId,
    Long referenceNumber,
    String createdBy,
    Instant createdAt) {}
