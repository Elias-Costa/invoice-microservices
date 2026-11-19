package dev.eliascosta.invoice.stock.adapter.in.rest;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import org.hibernate.validator.constraints.CodePointLength;

record CreateProductRequest(
    @NotBlank String code,
    @NotBlank @CodePointLength(max = 200) String description,
    @NotNull @DecimalMin("0.00") BigDecimal unitPrice,
    @NotNull @PositiveOrZero Integer initialBalance) {}
