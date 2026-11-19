package dev.eliascosta.invoice.stock.adapter.in.rest;

import java.math.BigDecimal;
import java.util.UUID;

record ProductResponse(
    UUID id,
    String code,
    String description,
    BigDecimal unitPrice,
    int balance,
    boolean active,
    long version) {}
