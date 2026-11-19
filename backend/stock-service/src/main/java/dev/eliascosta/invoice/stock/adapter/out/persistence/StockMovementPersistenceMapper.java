package dev.eliascosta.invoice.stock.adapter.out.persistence;

import dev.eliascosta.invoice.stock.domain.model.Quantity;
import dev.eliascosta.invoice.stock.domain.model.StockMovement;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper
interface StockMovementPersistenceMapper {

  @Mapping(target = "quantity", source = "quantity.value")
  @Mapping(target = "balanceAfter", source = "balanceAfter.value")
  StockMovementJpaEntity toEntity(StockMovement movement);

  StockMovement toDomain(StockMovementJpaEntity entity);

  default Quantity toQuantity(int value) {
    return new Quantity(value);
  }
}
