package dev.eliascosta.invoice.stock.domain.port.out;

import dev.eliascosta.invoice.stock.domain.model.Product;
import dev.eliascosta.invoice.stock.domain.model.ProductCode;
import dev.eliascosta.invoice.stock.domain.port.PageResult;
import dev.eliascosta.invoice.stock.domain.port.ProductSearch;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductRepository {

  Optional<Product> findById(UUID productId);

  List<Product> findAllById(Collection<UUID> productIds);

  boolean existsByCode(ProductCode code);

  PageResult<Product> search(ProductSearch query);

  Product save(Product product);
}
