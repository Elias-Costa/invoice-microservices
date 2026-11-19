package dev.eliascosta.invoice.billing.domain.port.out;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface StockGateway {

  /** Returns the products found in Stock, including inactive products. Missing IDs are omitted. */
  List<ProductSnapshot> findProducts(Collection<UUID> ids);

  record ProductSnapshot(
      UUID id,
      String code,
      String description,
      BigDecimal unitPrice,
      int balance,
      boolean active) {}
}
