package dev.eliascosta.invoice.stock.domain.port.in;

import dev.eliascosta.invoice.stock.domain.model.Product;
import java.util.UUID;

public interface GetProductUseCase {

  Product get(UUID productId);
}
