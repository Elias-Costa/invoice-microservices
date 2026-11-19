package dev.eliascosta.invoice.stock.adapter.in.rest;

import dev.eliascosta.invoice.common.idempotency.IdempotencyUserResolver;
import dev.eliascosta.invoice.common.idempotency.Idempotent;
import dev.eliascosta.invoice.stock.domain.port.in.ListStockMovementsUseCase;
import dev.eliascosta.invoice.stock.domain.port.in.RegisterStockMovementUseCase;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(ProductController.PATH + "/{id}/movements")
class StockMovementController {

  private final RegisterStockMovementUseCase registerMovement;
  private final ListStockMovementsUseCase listMovements;
  private final StockMovementRestMapper mapper;

  StockMovementController(
      RegisterStockMovementUseCase registerMovement,
      ListStockMovementsUseCase listMovements,
      StockMovementRestMapper mapper) {
    this.registerMovement = registerMovement;
    this.listMovements = listMovements;
    this.mapper = mapper;
  }

  @PostMapping
  @Idempotent
  @ResponseStatus(HttpStatus.CREATED)
  StockMovementResponse register(
      @PathVariable UUID id, @Valid @RequestBody RegisterStockMovementRequest request) {
    return mapper.toResponse(
        registerMovement.register(
            mapper.toCommand(id, request, IdempotencyUserResolver.ANONYMOUS)));
  }

  @GetMapping
  PageResponse<StockMovementResponse> list(
      @PathVariable UUID id,
      @RequestParam(defaultValue = "0") @PositiveOrZero int page,
      @RequestParam(defaultValue = "20") @Positive int size) {
    return PageResponse.from(listMovements.list(id, page, size), mapper::toResponse);
  }
}
