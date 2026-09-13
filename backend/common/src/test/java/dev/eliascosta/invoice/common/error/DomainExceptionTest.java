package dev.eliascosta.invoice.common.error;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class DomainExceptionTest {

  @Test
  void exposesTypeTitleDetailAndCause() {
    var cause = new IllegalStateException("boom");
    var ex =
        new UpstreamUnavailableException(
            "stock-service-unavailable", "Estoque indisponível", "Sem resposta do estoque.", cause);

    assertThat(ex.getType()).isEqualTo("stock-service-unavailable");
    assertThat(ex.getTitle()).isEqualTo("Estoque indisponível");
    assertThat(ex.getDetail()).isEqualTo("Sem resposta do estoque.").isEqualTo(ex.getMessage());
    assertThat(ex.getCause()).isSameAs(cause);
    assertThat(ex.getProperties()).isEmpty();
  }

  @Test
  void copiesPropertiesAndExposesThemReadOnly() {
    Map<String, Object> properties = new HashMap<>();
    properties.put("currentStatus", "PROCESSING");
    properties.put("allowedTransitions", List.of("CLOSED", "OPEN"));
    var ex =
        new InvalidStateException(
            "invalid-invoice-status", "Transição inválida", "Não permitido.", properties);

    properties.put("currentStatus", "CANCELED"); // later mutation must not leak in

    assertThat(ex.getProperties())
        .containsEntry("currentStatus", "PROCESSING")
        .containsEntry("allowedTransitions", List.of("CLOSED", "OPEN"));
    assertThatThrownBy(() -> ex.getProperties().put("x", "y"))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {"", " ", "Product-Not-Found", "product_not_found", "-leading", "trailing-"})
  void rejectsTypeThatIsNotACatalogueSlug(String type) {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new NotFoundException(type, "Título", "Detalhe"))
        .withMessageContaining("type");
  }

  @Test
  void rejectsNullType() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new NotFoundException(null, "Título", "Detalhe"))
        .withMessageContaining("type");
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "  "})
  void rejectsBlankTitle(String title) {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new ConflictException("product-code-already-exists", title, "Detalhe"))
        .withMessageContaining("title");
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "  "})
  void rejectsBlankDetail(String detail) {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new BusinessRuleException("insufficient-stock", "Título", detail))
        .withMessageContaining("detail");
  }

  @Test
  void rejectsNullProperties() {
    assertThatNullPointerException()
        .isThrownBy(
            () ->
                new InvalidRequestException(
                    "field-immutable", "Título", "Detalhe", (Map<String, Object>) null))
        .withMessageContaining("properties");
  }
}
