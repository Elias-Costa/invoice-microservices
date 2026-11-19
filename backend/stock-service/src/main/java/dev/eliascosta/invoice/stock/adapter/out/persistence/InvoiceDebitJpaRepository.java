package dev.eliascosta.invoice.stock.adapter.out.persistence;

import dev.eliascosta.invoice.stock.domain.model.InvoiceDebit.Outcome;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface InvoiceDebitJpaRepository extends JpaRepository<InvoiceDebitJpaEntity, UUID> {

  boolean existsByInvoiceIdAndOutcome(UUID invoiceId, Outcome outcome);
}
