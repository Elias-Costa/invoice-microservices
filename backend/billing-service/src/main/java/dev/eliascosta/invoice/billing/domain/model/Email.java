package dev.eliascosta.invoice.billing.domain.model;

import static dev.eliascosta.invoice.billing.domain.model.DomainValidation.invalid;
import static dev.eliascosta.invoice.billing.domain.model.DomainValidation.requiredText;

import java.util.regex.Pattern;

public record Email(String value) {

  private static final String ATOM = "[\\p{L}\\p{N}!#$%&'*+/=?^_`{|}~-]+";
  private static final String DOMAIN_LABEL =
      "[\\p{L}\\p{N}](?:[\\p{L}\\p{N}-]{0,61}[\\p{L}\\p{N}])?";
  private static final Pattern ADDRESS =
      Pattern.compile(
          ATOM + "(?:\\." + ATOM + ")*@" + DOMAIN_LABEL + "(?:\\." + DOMAIN_LABEL + ")*");

  public Email {
    requiredText(value, "email", 150);
    if (!ADDRESS.matcher(value).matches()) {
      throw invalid("email", "Informe um e-mail válido.");
    }
  }
}
