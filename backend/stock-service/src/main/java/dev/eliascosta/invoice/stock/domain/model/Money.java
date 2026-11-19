package dev.eliascosta.invoice.stock.domain.model;

import static dev.eliascosta.invoice.stock.domain.model.DomainValidation.invalid;

import java.math.BigDecimal;
import java.math.RoundingMode;

public record Money(BigDecimal value) {

  private static final BigDecimal HALF_CENT = new BigDecimal("0.005");
  private static final BigDecimal ZERO = new BigDecimal("0.00");

  // First amount that rounds outside numeric(15,2).
  private static final BigDecimal LIMIT = new BigDecimal("9999999999999.995");

  public Money {
    if (value == null || value.signum() < 0) {
      throw invalid(
          "unitPrice", "O preço unitário é obrigatório e deve ser maior ou igual a zero.");
    }
    if (value.compareTo(LIMIT) >= 0) {
      throw invalid(
          "unitPrice", "O preço unitário deve caber em 13 dígitos inteiros e 2 decimais.");
    }
    value = value.compareTo(HALF_CENT) < 0 ? ZERO : value.setScale(2, RoundingMode.HALF_UP);
  }
}
