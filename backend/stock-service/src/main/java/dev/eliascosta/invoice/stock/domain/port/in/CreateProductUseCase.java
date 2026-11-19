package dev.eliascosta.invoice.stock.domain.port.in;

import dev.eliascosta.invoice.stock.domain.model.Money;
import dev.eliascosta.invoice.stock.domain.model.Product;
import dev.eliascosta.invoice.stock.domain.model.ProductCode;
import dev.eliascosta.invoice.stock.domain.model.Quantity;

public interface CreateProductUseCase {

  Product create(Command command);

  record Command(
      ProductCode code,
      String description,
      Money unitPrice,
      Quantity initialBalance,
      String createdBy) {}
}
