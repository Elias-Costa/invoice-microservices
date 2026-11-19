package dev.eliascosta.invoice.stock.adapter.out.persistence;

import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.Repository;

interface StockMovementJpaRepository extends Repository<StockMovementJpaEntity, UUID> {

  Page<StockMovementJpaEntity> findByProductId(UUID productId, Pageable pageable);
}
