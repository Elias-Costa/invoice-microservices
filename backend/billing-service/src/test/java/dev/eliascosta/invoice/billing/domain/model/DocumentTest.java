package dev.eliascosta.invoice.billing.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.eliascosta.invoice.common.error.InvalidRequestException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class DocumentTest {

  @ParameterizedTest
  @CsvSource({
    "28001238938, CPF",
    "52998224725, CPF",
    "01234567890, CPF",
    "11222333000181, CNPJ",
    "45678912000155, CNPJ",
    "04252011000110, CNPJ"
  })
  void checksDigitsAndDerivesType(String value, Document.Type type) {
    Document document = new Document(value);

    assertThat(document.value()).isEqualTo(value);
    assertThat(document.type()).isEqualTo(type);
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(
      strings = {
        "28001238948",
        "28001238939",
        "11222333000191",
        "11222333000182",
        "11111111111",
        "00000000000000",
        "123",
        "529.982.247-25",
        " 52998224725",
        "52A98224725",
        "５２９９８２２４７２５"
      })
  void rejectsInvalidDocumentsWithFieldViolation(String value) {
    assertThatThrownBy(() -> new Document(value))
        .isInstanceOfSatisfying(
            InvalidRequestException.class,
            error -> {
              assertThat(error.getType()).isEqualTo("validation-error");
              assertThat(error.getProperties())
                  .containsEntry(
                      "errors", List.of(Map.of("field", "document", "message", error.getDetail())));
            });
  }
}
