package dev.eliascosta.invoice.stock.adapter.out.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

interface ProductJpaRepository
    extends JpaRepository<ProductJpaEntity, UUID>, JpaSpecificationExecutor<ProductJpaEntity> {

  boolean existsByCode(String code);
}
