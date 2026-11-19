package dev.eliascosta.invoice.stock.domain.model;

import static dev.eliascosta.invoice.stock.domain.model.DomainValidation.invalid;

import java.util.Locale;
import java.util.regex.Pattern;

public record ProductCode(String value) {

  private static final Pattern FORMAT = Pattern.compile("[A-Z0-9][A-Z0-9._-]{0,29}");

  public ProductCode {
    if (value == null) {
      throw invalid("code", "O código é obrigatório.");
    }
    value = value.trim().toUpperCase(Locale.ROOT);
    if (!FORMAT.matcher(value).matches()) {
      throw invalid("code", "O código deve seguir o formato [A-Z0-9][A-Z0-9._-]{0,29}.");
    }
  }
}
