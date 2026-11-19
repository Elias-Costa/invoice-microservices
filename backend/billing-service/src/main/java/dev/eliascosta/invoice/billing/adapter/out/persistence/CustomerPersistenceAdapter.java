package dev.eliascosta.invoice.billing.adapter.out.persistence;

import dev.eliascosta.invoice.billing.domain.model.Customer;
import dev.eliascosta.invoice.billing.domain.model.Document;
import dev.eliascosta.invoice.billing.domain.port.PageResult;
import dev.eliascosta.invoice.billing.domain.port.out.CustomerRepository;
import jakarta.persistence.EntityManager;
import java.util.Optional;
import java.util.UUID;
import org.hibernate.query.criteria.HibernateCriteriaBuilder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.data.jpa.domain.Specification;
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

  @Override
  public PageResult<Summary> search(Search query) {
    Sort requested = Sort.by(query.ascending() ? Direction.ASC : Direction.DESC, query.sortBy());
    PageRequest pageable =
        PageRequest.of(query.page(), Math.min(query.size(), 100), requested.and(Sort.by("id")));
    Page<Summary> result =
        repository.findBy(matches(query), rows -> rows.as(Summary.class).page(pageable));
    return new PageResult<>(
        result.getContent(), result.getNumber(), result.getSize(), result.getTotalElements());
  }

  private static Specification<CustomerJpaEntity> matches(Search query) {
    return (customer, criteria, builder) -> {
      var active = builder.equal(customer.get("active"), query.active());
      if (query.search() == null || query.search().isBlank()) {
        return active;
      }

      String prefix = query.search().replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
      HibernateCriteriaBuilder hibernate = (HibernateCriteriaBuilder) builder;
      return builder.and(
          active,
          builder.or(
              hibernate.ilike(customer.get("name"), "%" + prefix, '!'),
              builder.like(customer.get("document"), prefix, '!')));
    };
  }
}
