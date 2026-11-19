package dev.eliascosta.invoice.billing.adapter.in.rest;

import jakarta.validation.constraints.NotBlank;
import org.hibernate.validator.constraints.CodePointLength;

record CreateCustomerRequest(
    @NotBlank @CodePointLength(max = 150) String name,
    @NotBlank String document,
    @CodePointLength(max = 150) String email,
    @CodePointLength(max = 20) String phone) {}
