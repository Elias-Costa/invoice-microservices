package dev.eliascosta.invoice.billing.adapter.in.rest;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import org.hibernate.validator.constraints.CodePointLength;
import tools.jackson.databind.JsonNode;

record UpdateCustomerRequest(
    @NotBlank @CodePointLength(max = 150) String name,
    @CodePointLength(max = 150) String email,
    @CodePointLength(max = 20) String phone,
    @NotNull @PositiveOrZero Long version,
    JsonNode document,
    JsonNode active) {}
