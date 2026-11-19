package dev.eliascosta.invoice.billing.adapter.out.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

interface CustomerJpaRepository
    extends JpaRepository<CustomerJpaEntity, UUID>, JpaSpecificationExecutor<CustomerJpaEntity> {

  boolean existsByDocument(String document);
}
