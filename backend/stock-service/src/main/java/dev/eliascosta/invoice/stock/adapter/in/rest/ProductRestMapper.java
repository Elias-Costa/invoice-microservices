package dev.eliascosta.invoice.stock.adapter.in.rest;

import dev.eliascosta.invoice.stock.domain.model.Money;
import dev.eliascosta.invoice.stock.domain.model.Product;
import dev.eliascosta.invoice.stock.domain.model.ProductCode;
import dev.eliascosta.invoice.stock.domain.model.Quantity;
import dev.eliascosta.invoice.stock.domain.port.in.CreateProductUseCase;
import dev.eliascosta.invoice.stock.domain.port.in.UpdateProductUseCase;
import java.math.BigDecimal;
import java.util.UUID;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper
interface ProductRestMapper {

  @Mapping(target = "code", source = "code.value")
  @Mapping(target = "unitPrice", source = "unitPrice.value")
  @Mapping(target = "balance", source = "balance.value")
  ProductResponse toResponse(Product product);

  @Mapping(target = "code", source = "code.value")
  @Mapping(target = "unitPrice", source = "unitPrice.value")
  @Mapping(target = "balance", source = "balance.value")
  BatchProductResponse toBatchResponse(Product product);

  CreateProductUseCase.Command toCreateCommand(CreateProductRequest request, String createdBy);

  UpdateProductUseCase.Command toUpdateCommand(
      UUID productId, UpdateProductRequest request, String updatedBy);

  default ProductCode toCode(String value) {
    return value == null ? null : new ProductCode(value);
  }

  default Money toMoney(BigDecimal value) {
    return new Money(value);
  }

  default Quantity toQuantity(Integer value) {
    return new Quantity(value);
  }
}
