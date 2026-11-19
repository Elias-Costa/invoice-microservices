package dev.eliascosta.invoice.stock.adapter.in.rest;

import dev.eliascosta.invoice.common.error.InvalidRequestException;
import dev.eliascosta.invoice.common.error.ProblemTypes;
import dev.eliascosta.invoice.common.idempotency.IdempotencyUserResolver;
import dev.eliascosta.invoice.common.idempotency.Idempotent;
import dev.eliascosta.invoice.stock.domain.exception.ImmutableProductFieldException;
import dev.eliascosta.invoice.stock.domain.port.ProductSearch;
import dev.eliascosta.invoice.stock.domain.port.in.CreateProductUseCase;
import dev.eliascosta.invoice.stock.domain.port.in.DeactivateProductUseCase;
import dev.eliascosta.invoice.stock.domain.port.in.GetProductUseCase;
import dev.eliascosta.invoice.stock.domain.port.in.GetProductsByIdsUseCase;
import dev.eliascosta.invoice.stock.domain.port.in.ListProductsUseCase;
import dev.eliascosta.invoice.stock.domain.port.in.ReactivateProductUseCase;
import dev.eliascosta.invoice.stock.domain.port.in.UpdateProductUseCase;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(ProductController.PATH)
class ProductController {

  static final String PATH = "/api/stock/products";
  private static final String AUDIT_USER = IdempotencyUserResolver.ANONYMOUS;
  private static final Set<String> SORT_FIELDS =
      Set.of("code", "description", "unitPrice", "balance", "active");

  private final CreateProductUseCase createProduct;
  private final UpdateProductUseCase updateProduct;
  private final GetProductUseCase getProduct;
  private final GetProductsByIdsUseCase getProductsByIds;
  private final ListProductsUseCase listProducts;
  private final DeactivateProductUseCase deactivateProduct;
  private final ReactivateProductUseCase reactivateProduct;
  private final ProductRestMapper mapper;

  ProductController(
      CreateProductUseCase createProduct,
      UpdateProductUseCase updateProduct,
      GetProductUseCase getProduct,
      GetProductsByIdsUseCase getProductsByIds,
      ListProductsUseCase listProducts,
      DeactivateProductUseCase deactivateProduct,
      ReactivateProductUseCase reactivateProduct,
      ProductRestMapper mapper) {
    this.createProduct = createProduct;
    this.updateProduct = updateProduct;
    this.getProduct = getProduct;
    this.getProductsByIds = getProductsByIds;
    this.listProducts = listProducts;
    this.deactivateProduct = deactivateProduct;
    this.reactivateProduct = reactivateProduct;
    this.mapper = mapper;
  }

  @PostMapping
  @Idempotent
  ResponseEntity<ProductResponse> create(@Valid @RequestBody CreateProductRequest request) {
    ProductResponse product =
        mapper.toResponse(createProduct.create(mapper.toCreateCommand(request, AUDIT_USER)));
    return ResponseEntity.created(URI.create(PATH + "/" + product.id())).body(product);
  }

  @GetMapping
  PageResponse<ProductResponse> list(
      @RequestParam(required = false) String search,
      @RequestParam(defaultValue = "true") boolean active,
      @RequestParam(defaultValue = "0") @PositiveOrZero int page,
      @RequestParam(defaultValue = "20") @Positive int size,
      @RequestParam(required = false) String sort) {
    String[] parts = sort == null ? new String[] {"code", "asc"} : sort.split(",", -1);
    if (parts.length != 2
        || !SORT_FIELDS.contains(parts[0])
        || !(parts[1].equals("asc") || parts[1].equals("desc"))) {
      String message =
          "Use code, description, unitPrice, balance ou active, seguido de asc ou desc.";
      throw new InvalidRequestException(
          ProblemTypes.VALIDATION_ERROR,
          "Dados inválidos",
          "Ordenação inválida.",
          Map.of("errors", List.of(Map.of("field", "sort", "message", message))));
    }
    ProductSearch query =
        new ProductSearch(search, active, page, size, parts[0], parts[1].equals("asc"));
    return PageResponse.from(listProducts.list(query), mapper::toResponse);
  }

  @GetMapping("/{id}")
  ProductResponse get(@PathVariable UUID id) {
    return mapper.toResponse(getProduct.get(id));
  }

  @GetMapping("/batch")
  List<BatchProductResponse> batch(
      @RequestParam @Size(min = 1, max = 100) List<@NotNull UUID> ids) {
    return getProductsByIds.getByIds(ids).stream().map(mapper::toBatchResponse).toList();
  }

  @PutMapping("/{id}")
  ProductResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateProductRequest request) {
    if (request.balance() != null) {
      throw new ImmutableProductFieldException(
          "balance", "O saldo do produto só pode ser alterado por uma movimentação de estoque.");
    }
    return mapper.toResponse(updateProduct.update(mapper.toUpdateCommand(id, request, AUDIT_USER)));
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void deactivate(@PathVariable UUID id) {
    deactivateProduct.deactivate(id, AUDIT_USER);
  }

  @PostMapping("/{id}/reactivate")
  ProductResponse reactivate(@PathVariable UUID id) {
    return mapper.toResponse(reactivateProduct.reactivate(id, AUDIT_USER));
  }
}
