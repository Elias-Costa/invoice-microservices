package dev.eliascosta.invoice.stock.domain.port.in;

import dev.eliascosta.invoice.stock.domain.model.Product;
import java.util.UUID;

public interface ReactivateProductUseCase {

  Product reactivate(UUID productId, String updatedBy);
}
