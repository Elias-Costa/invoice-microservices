package dev.eliascosta.invoice.stock.domain.exception;

import static org.assertj.core.api.Assertions.assertThat;

import dev.eliascosta.invoice.common.error.ConflictException;
import dev.eliascosta.invoice.common.error.NotFoundException;
import dev.eliascosta.invoice.stock.domain.model.ProductCode;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ProductExceptionsTest {

  @Test
  void missingProductUsesTheNotFoundCatalogueEntry() {
    UUID id = UUID.randomUUID();
    NotFoundException error = new ProductNotFoundException(id);

    assertThat(error.getType()).isEqualTo("product-not-found");
    assertThat(error.getDetail()).contains(id.toString());
  }

  @Test
  void conflictingProductCodeUsesTheCanonicalCodeAndConflictCatalogueEntry() {
    ConflictException error = new ProductCodeAlreadyExistsException(new ProductCode(" par-m6 "));

    assertThat(error.getType()).isEqualTo("product-code-already-exists");
    assertThat(error.getDetail()).contains("PAR-M6");
  }
}
