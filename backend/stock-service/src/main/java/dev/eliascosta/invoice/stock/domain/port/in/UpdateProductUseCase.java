package dev.eliascosta.invoice.stock.domain.port.in;

import dev.eliascosta.invoice.stock.domain.model.Money;
import dev.eliascosta.invoice.stock.domain.model.Product;
import dev.eliascosta.invoice.stock.domain.model.ProductCode;
import java.util.UUID;

public interface UpdateProductUseCase {

  Product update(Command command);

  /** An optional code supplied by the caller is checked against the persisted product. */
  record Command(
      UUID productId,
      ProductCode code,
      String description,
      Money unitPrice,
      long version,
      String updatedBy) {}
}
