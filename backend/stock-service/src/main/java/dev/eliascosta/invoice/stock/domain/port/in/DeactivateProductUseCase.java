package dev.eliascosta.invoice.stock.domain.port.in;

import java.util.UUID;

public interface DeactivateProductUseCase {

  void deactivate(UUID productId, String updatedBy);
}
