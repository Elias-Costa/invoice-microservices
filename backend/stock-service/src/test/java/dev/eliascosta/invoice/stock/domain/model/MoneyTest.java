package dev.eliascosta.invoice.stock.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import dev.eliascosta.invoice.common.error.InvalidRequestException;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class MoneyTest {

  @ParameterizedTest
  @CsvSource({
    "0, 0.00",
    "0.005, 0.01",
    "2.344, 2.34",
    "2.345, 2.35",
    "1E-2147483647, 0.00",
    "9999999999999.994, 9999999999999.99"
  })
  void roundsHalfUpToTwoDecimals(String input, String expected) {
    assertThat(new Money(new BigDecimal(input)).value()).isEqualTo(new BigDecimal(expected));
    assertThat(new Money(new BigDecimal(input)).value().scale()).isEqualTo(2);
  }

  @ParameterizedTest
  @ValueSource(strings = {"-0.001", "9999999999999.995", "10000000000000", "1E+2147483647"})
  void rejectsNegativePricesAndPricesThatDoNotFitTheSchema(String value) {
    assertThatExceptionOfType(InvalidRequestException.class)
        .isThrownBy(() -> new Money(new BigDecimal(value)));
  }

  @Test
  void rejectsMissingPrice() {
    assertThatExceptionOfType(InvalidRequestException.class).isThrownBy(() -> new Money(null));
  }
}
