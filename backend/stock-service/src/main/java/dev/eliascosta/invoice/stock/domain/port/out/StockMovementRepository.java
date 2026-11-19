package dev.eliascosta.invoice.stock.domain.port.out;

import dev.eliascosta.invoice.stock.domain.model.StockMovement;
import dev.eliascosta.invoice.stock.domain.port.PageResult;
import java.util.UUID;

public interface StockMovementRepository {

  void append(StockMovement movement);

  /** Returns movements ordered by creation time descending. */
  PageResult<StockMovement> findByProductId(UUID productId, int page, int size);
}
