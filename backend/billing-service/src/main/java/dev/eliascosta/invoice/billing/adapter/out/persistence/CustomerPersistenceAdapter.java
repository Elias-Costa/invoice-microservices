package dev.eliascosta.invoice.billing.adapter.out.persistence;

import dev.eliascosta.invoice.billing.domain.model.Customer;
import dev.eliascosta.invoice.billing.domain.model.Document;
import dev.eliascosta.invoice.billing.domain.port.out.CustomerRepository;
import jakarta.persistence.EntityManager;
import java.util.Optional;
import java.util.UUID;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Repository;

@Repository
class CustomerPersistenceAdapter implements CustomerRepository {

  private final CustomerJpaRepository repository;
  private final CustomerPersistenceMapper mapper;
  private final EntityManager entityManager;

  CustomerPersistenceAdapter(
      CustomerJpaRepository repository,
      CustomerPersistenceMapper mapper,
      EntityManager entityManager) {
    this.repository = repository;
    this.mapper = mapper;
    this.entityManager = entityManager;
  }

  @Override
  public Optional<Customer> findById(UUID id) {
    return repository.findById(id).map(mapper::toDomain);
  }

  @Override
  public boolean existsByDocument(Document document) {
    return repository.existsByDocument(document.value());
  }

  @Override
  public Customer save(Customer customer) {
    CustomerJpaEntity entity = entityManager.find(CustomerJpaEntity.class, customer.getId());
    long currentVersion = entity == null ? 0 : entity.getVersion();
    if (customer.getVersion() != currentVersion) {
      throw new ObjectOptimisticLockingFailureException(CustomerJpaEntity.class, customer.getId());
    }
    if (entity == null) {
      entity = mapper.toEntity(customer);
      entityManager.persist(entity);
    } else {
      mapper.updateEntity(customer, entity);
    }
    entityManager.flush();
    return mapper.toDomain(entity);
  }
}
