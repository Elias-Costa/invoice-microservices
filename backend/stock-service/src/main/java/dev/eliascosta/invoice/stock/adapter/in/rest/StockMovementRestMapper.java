package dev.eliascosta.invoice.stock.adapter.in.rest;

import dev.eliascosta.invoice.stock.domain.model.Quantity;
import dev.eliascosta.invoice.stock.domain.model.StockMovement;
import dev.eliascosta.invoice.stock.domain.port.in.RegisterStockMovementUseCase;
import java.util.UUID;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper
interface StockMovementRestMapper {

  RegisterStockMovementUseCase.Command toCommand(
      UUID productId, RegisterStockMovementRequest request, String createdBy);

  @Mapping(target = "quantity", source = "quantity.value")
  @Mapping(target = "balanceAfter", source = "balanceAfter.value")
  StockMovementResponse toResponse(StockMovement movement);

  default Quantity toQuantity(int value) {
    return new Quantity(value);
  }
}
