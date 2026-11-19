package dev.eliascosta.invoice.billing.domain.model;

import static dev.eliascosta.invoice.billing.domain.model.DomainValidation.invalid;

public record Quantity(int value) {

  public Quantity {
    if (value < 1) {
      throw invalid("quantity", "A quantidade deve ser maior que zero.");
    }
  }
}
