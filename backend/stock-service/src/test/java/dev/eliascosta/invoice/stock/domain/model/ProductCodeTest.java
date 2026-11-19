package dev.eliascosta.invoice.stock.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import dev.eliascosta.invoice.common.error.InvalidRequestException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class ProductCodeTest {

  @Test
  void normalizesBeforeComparingCodes() {
    ProductCode code = new ProductCode(" \tpino-m6_a.2\n");

    assertThat(code.value()).isEqualTo("PINO-M6_A.2");
    assertThat(code).isEqualTo(new ProductCode("PINO-M6_A.2"));
  }

  @Test
  void acceptsCodesAtTheLengthBoundaries() {
    assertThat(new ProductCode("7").value()).isEqualTo("7");
    assertThat(new ProductCode("A".repeat(30)).value()).hasSize(30);
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {" ", "_PIN", "PI NO", "PÍNO", "PIN/1", "1234567890123456789012345678901"})
  void rejectsInvalidCodes(String value) {
    assertThatExceptionOfType(InvalidRequestException.class)
        .isThrownBy(() -> new ProductCode(value))
        .satisfies(error -> assertThat(error.getType()).isEqualTo("validation-error"));
  }
}
