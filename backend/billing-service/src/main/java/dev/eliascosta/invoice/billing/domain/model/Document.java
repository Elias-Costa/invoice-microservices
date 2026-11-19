package dev.eliascosta.invoice.billing.domain.model;

import static dev.eliascosta.invoice.billing.domain.model.DomainValidation.invalid;

import java.util.regex.Pattern;

public record Document(String value) {

  private static final int CPF_LENGTH = 11;
  private static final Pattern DIGITS = Pattern.compile("[0-9]{11}|[0-9]{14}");
  private static final Pattern REPEATED_DIGIT = Pattern.compile("([0-9])\\1+");

  public Document {
    if (value == null || !DIGITS.matcher(value).matches()) {
      throw invalid("document", "Informe um CPF ou CNPJ contendo somente dígitos.");
    }
    if (REPEATED_DIGIT.matcher(value).matches() || !hasValidCheckDigits(value)) {
      throw invalid("document", "O CPF ou CNPJ informado é inválido.");
    }
  }

  public Type type() {
    return value.length() == CPF_LENGTH ? Type.CPF : Type.CNPJ;
  }

  private static boolean hasValidCheckDigits(String value) {
    int firstCheckDigitIndex = value.length() - 2;
    return checkDigit(value, firstCheckDigitIndex) == value.charAt(firstCheckDigitIndex) - '0'
        && checkDigit(value, firstCheckDigitIndex + 1)
            == value.charAt(firstCheckDigitIndex + 1) - '0';
  }

  private static int checkDigit(String value, int end) {
    int highestWeight = value.length() == CPF_LENGTH ? 11 : 9;
    int sum = 0;
    int weight = 2;
    for (int i = end - 1; i >= 0; i--) {
      sum += (value.charAt(i) - '0') * weight;
      weight = weight == highestWeight ? 2 : weight + 1;
    }
    int remainder = sum % 11;
    return remainder < 2 ? 0 : 11 - remainder;
  }

  public enum Type {
    CPF,
    CNPJ
  }
}
