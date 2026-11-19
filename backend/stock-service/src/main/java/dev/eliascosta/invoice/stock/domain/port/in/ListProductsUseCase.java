package dev.eliascosta.invoice.stock.domain.port.in;

import dev.eliascosta.invoice.stock.domain.model.Product;
import dev.eliascosta.invoice.stock.domain.port.PageResult;
import dev.eliascosta.invoice.stock.domain.port.ProductSearch;

public interface ListProductsUseCase {

  PageResult<Product> list(ProductSearch query);
}
