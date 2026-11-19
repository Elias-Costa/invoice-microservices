package dev.eliascosta.invoice.billing.adapter.in.rest;

import dev.eliascosta.invoice.billing.domain.model.Document;
import java.util.UUID;

record CustomerResponse(
    UUID id,
    String name,
    String document,
    Document.Type documentType,
    String email,
    String phone,
    boolean active,
    long version) {}
