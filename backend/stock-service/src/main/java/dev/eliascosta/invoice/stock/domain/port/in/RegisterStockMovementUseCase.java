package dev.eliascosta.invoice.stock.domain.port.in;

import dev.eliascosta.invoice.stock.domain.model.MovementType;
import dev.eliascosta.invoice.stock.domain.model.Quantity;
import dev.eliascosta.invoice.stock.domain.model.StockMovement;
import java.util.UUID;

public interface RegisterStockMovementUseCase {

  StockMovement register(Command command);

  /** Registers an INBOUND or OUTBOUND movement. */
  record Command(
      UUID productId, MovementType type, Quantity quantity, String reason, String createdBy) {}
}
