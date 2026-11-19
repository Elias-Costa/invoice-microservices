package dev.eliascosta.invoice.stock.domain.port;

public record ProductSearch(
    String search, Boolean active, int page, int size, String sortBy, boolean ascending) {}
