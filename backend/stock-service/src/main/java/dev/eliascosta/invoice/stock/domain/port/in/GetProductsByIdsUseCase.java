package dev.eliascosta.invoice.stock.domain.port.in;

import dev.eliascosta.invoice.stock.domain.model.Product;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface GetProductsByIdsUseCase {

  /** Returns existing products for the supplied IDs, including inactive products. */
  List<Product> getByIds(Collection<UUID> productIds);
}
