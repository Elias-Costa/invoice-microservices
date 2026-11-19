package dev.eliascosta.invoice.stock.adapter.out.persistence;

import dev.eliascosta.invoice.stock.domain.model.Money;
import dev.eliascosta.invoice.stock.domain.model.Product;
import dev.eliascosta.invoice.stock.domain.model.ProductCode;
import dev.eliascosta.invoice.stock.domain.model.Quantity;
import java.math.BigDecimal;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper
interface ProductPersistenceMapper {

  @Mapping(target = "code", source = "code.value")
  @Mapping(target = "unitPrice", source = "unitPrice.value")
  @Mapping(target = "balance", source = "balance.value")
  ProductJpaEntity toEntity(Product product);

  @Mapping(target = "unitPrice", source = "unitPrice.value")
  @Mapping(target = "balance", source = "balance.value")
  void updateEntity(Product product, @MappingTarget ProductJpaEntity entity);

  Product toDomain(ProductJpaEntity entity);

  default ProductCode toCode(String value) {
    return new ProductCode(value);
  }

  default Money toMoney(BigDecimal value) {
    return new Money(value);
  }

  default Quantity toQuantity(int value) {
    return new Quantity(value);
  }
}
