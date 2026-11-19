package dev.eliascosta.invoice.billing.adapter.out.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface CustomerJpaRepository extends JpaRepository<CustomerJpaEntity, UUID> {

  boolean existsByDocument(String document);
}
