package dev.eliascosta.invoice.stock.domain.port.in;

import dev.eliascosta.invoice.stock.domain.model.StockMovement;
import dev.eliascosta.invoice.stock.domain.port.PageResult;
import java.util.UUID;

public interface ListStockMovementsUseCase {

  PageResult<StockMovement> list(UUID productId, int page, int size);
}
