package dev.eliascosta.invoice.stock.adapter.out.persistence;

import dev.eliascosta.invoice.stock.domain.model.StockMovement;
import dev.eliascosta.invoice.stock.domain.port.PageResult;
import dev.eliascosta.invoice.stock.domain.port.out.StockMovementRepository;
import jakarta.persistence.EntityManager;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

@Repository
class StockMovementPersistenceAdapter implements StockMovementRepository {

  private final StockMovementJpaRepository repository;
  private final StockMovementPersistenceMapper mapper;
  private final EntityManager entityManager;

  StockMovementPersistenceAdapter(
      StockMovementJpaRepository repository,
      StockMovementPersistenceMapper mapper,
      EntityManager entityManager) {
    this.repository = repository;
    this.mapper = mapper;
    this.entityManager = entityManager;
  }

  @Override
  public void append(StockMovement movement) {
    entityManager.persist(mapper.toEntity(movement));
  }

  @Override
  public PageResult<StockMovement> findByProductId(UUID productId, int page, int size) {
    PageRequest pageable =
        PageRequest.of(page, Math.min(size, 100), Sort.by(Sort.Direction.DESC, "createdAt", "id"));
    Page<StockMovement> result =
        repository.findByProductId(productId, pageable).map(mapper::toDomain);
    return new PageResult<>(
        result.getContent(), result.getNumber(), result.getSize(), result.getTotalElements());
  }
}
