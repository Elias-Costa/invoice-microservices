package dev.eliascosta.invoice.stock.application.service;

import dev.eliascosta.invoice.common.error.ConflictException;
import dev.eliascosta.invoice.common.error.ProblemTypes;
import dev.eliascosta.invoice.stock.domain.exception.ImmutableProductFieldException;
import dev.eliascosta.invoice.stock.domain.exception.ProductCodeAlreadyExistsException;
import dev.eliascosta.invoice.stock.domain.exception.ProductNotFoundException;
import dev.eliascosta.invoice.stock.domain.model.Product;
import dev.eliascosta.invoice.stock.domain.port.PageResult;
import dev.eliascosta.invoice.stock.domain.port.ProductSearch;
import dev.eliascosta.invoice.stock.domain.port.in.CreateProductUseCase;
import dev.eliascosta.invoice.stock.domain.port.in.DeactivateProductUseCase;
import dev.eliascosta.invoice.stock.domain.port.in.GetProductUseCase;
import dev.eliascosta.invoice.stock.domain.port.in.GetProductsByIdsUseCase;
import dev.eliascosta.invoice.stock.domain.port.in.ListProductsUseCase;
import dev.eliascosta.invoice.stock.domain.port.in.ReactivateProductUseCase;
import dev.eliascosta.invoice.stock.domain.port.in.UpdateProductUseCase;
import dev.eliascosta.invoice.stock.domain.port.out.ProductRepository;
import dev.eliascosta.invoice.stock.domain.port.out.StockMovementRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
class ProductService
    implements CreateProductUseCase,
        UpdateProductUseCase,
        ListProductsUseCase,
        GetProductUseCase,
        GetProductsByIdsUseCase,
        DeactivateProductUseCase,
        ReactivateProductUseCase {

  private final ProductRepository products;
  private final StockMovementRepository movements;
  private final Clock clock;

  ProductService(ProductRepository products, StockMovementRepository movements, Clock clock) {
    this.products = products;
    this.movements = movements;
    this.clock = clock;
  }

  @Override
  @Transactional
  public Product create(CreateProductUseCase.Command command) {
    if (products.existsByCode(command.code())) {
      throw new ProductCodeAlreadyExistsException(command.code());
    }

    Product.Creation creation =
        Product.create(
            command.code(),
            command.description(),
            command.unitPrice(),
            command.initialBalance(),
            command.createdBy(),
            clock.instant());

    Product saved;
    try {
      saved = products.save(creation.product());
    } catch (DataIntegrityViolationException failure) {
      if (isProductCodeConflict(failure)) {
        throw new ProductCodeAlreadyExistsException(command.code());
      }
      throw failure;
    }
    movements.append(creation.initialMovement());
    return saved;
  }

  @Override
  @Transactional
  public Product update(UpdateProductUseCase.Command command) {
    Product product = get(command.productId());
    if (command.code() != null && !command.code().equals(product.getCode())) {
      throw new ImmutableProductFieldException("code", "O código do produto é imutável.");
    }
    if (command.version() != product.getVersion()) {
      throw new ConflictException(
          ProblemTypes.OPTIMISTIC_LOCK_CONFLICT,
          "Conflito de edição",
          "O produto foi alterado. Recarregue os dados e tente novamente.");
    }

    Instant now = clock.instant();
    product.changeDescription(command.description(), command.updatedBy(), now);
    product.changePrice(command.unitPrice(), command.updatedBy(), now);
    return products.save(product);
  }

  @Override
  public PageResult<Product> list(ProductSearch query) {
    return products.search(query);
  }

  @Override
  public Product get(UUID productId) {
    return products.findById(productId).orElseThrow(() -> new ProductNotFoundException(productId));
  }

  @Override
  public List<Product> getByIds(Collection<UUID> productIds) {
    return products.findAllById(productIds);
  }

  @Override
  @Transactional
  public void deactivate(UUID productId, String updatedBy) {
    Product product = get(productId);
    if (product.isActive()) {
      product.deactivate(updatedBy, clock.instant());
      products.save(product);
    }
  }

  @Override
  @Transactional
  public Product reactivate(UUID productId, String updatedBy) {
    Product product = get(productId);
    if (product.isActive()) {
      return product;
    }
    product.reactivate(updatedBy, clock.instant());
    return products.save(product);
  }

  private static boolean isProductCodeConflict(DataIntegrityViolationException failure) {
    for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
      if (cause instanceof ConstraintViolationException violation
          && "uk_products_code".equals(violation.getConstraintName())) {
        return true;
      }
    }
    return false;
  }
}
