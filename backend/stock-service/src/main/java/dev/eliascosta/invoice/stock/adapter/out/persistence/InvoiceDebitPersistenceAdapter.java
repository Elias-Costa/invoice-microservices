package dev.eliascosta.invoice.stock.adapter.out.persistence;

import dev.eliascosta.invoice.stock.domain.model.InvoiceDebit;
import dev.eliascosta.invoice.stock.domain.model.InvoiceDebit.Outcome;
import dev.eliascosta.invoice.stock.domain.port.out.InvoiceDebitRepository;
import jakarta.persistence.EntityManager;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.json.JsonMapper;

@Repository
class InvoiceDebitPersistenceAdapter implements InvoiceDebitRepository {

  private final InvoiceDebitJpaRepository repository;
  private final InvoiceDebitPersistenceMapper mapper;
  private final EntityManager entityManager;
  private final JsonMapper json;

  InvoiceDebitPersistenceAdapter(
      InvoiceDebitJpaRepository repository,
      InvoiceDebitPersistenceMapper mapper,
      EntityManager entityManager,
      JsonMapper json) {
    this.repository = repository;
    this.mapper = mapper;
    this.entityManager = entityManager;
    this.json = json;
  }

  @Override
  public Optional<InvoiceDebit> findByPrintRequestId(UUID printRequestId) {
    return repository.findById(printRequestId).map(entity -> mapper.toDomain(entity, json));
  }

  @Override
  public boolean hasConfirmedDebit(UUID invoiceId) {
    return repository.existsByInvoiceIdAndOutcome(invoiceId, Outcome.CONFIRMED);
  }

  @Override
  public void append(InvoiceDebit debit) {
    entityManager.persist(mapper.toEntity(debit, json));
  }
}
