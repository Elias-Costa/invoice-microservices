package dev.eliascosta.invoice.stock.domain.model;

import static dev.eliascosta.invoice.stock.domain.model.DomainValidation.invalid;

public record Quantity(int value) {

  public Quantity {
    if (value < 0) {
      throw invalid("quantity", "A quantidade deve ser maior ou igual a zero.");
    }
  }

  void requirePositive() {
    if (value == 0) {
      throw invalid("quantity", "A quantidade movimentada deve ser maior que zero.");
    }
  }

  Quantity plus(Quantity quantity) {
    if (quantity.value > Integer.MAX_VALUE - value) {
      throw invalid("quantity", "A quantidade informada ultrapassa o limite do saldo.");
    }
    return new Quantity(value + quantity.value);
  }

  Quantity minus(Quantity quantity) {
    return new Quantity(value - quantity.value);
  }

  boolean isLessThan(Quantity quantity) {
    return value < quantity.value;
  }
}
