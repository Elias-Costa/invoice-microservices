package dev.eliascosta.invoice.stock.adapter.out.persistence;

import dev.eliascosta.invoice.stock.domain.model.Product;
import dev.eliascosta.invoice.stock.domain.model.ProductCode;
import dev.eliascosta.invoice.stock.domain.port.PageResult;
import dev.eliascosta.invoice.stock.domain.port.ProductSearch;
import dev.eliascosta.invoice.stock.domain.port.out.ProductRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
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
class ProductPersistenceAdapter implements ProductRepository {

  private final ProductJpaRepository repository;
  private final ProductPersistenceMapper mapper;
  private final EntityManager entityManager;

  ProductPersistenceAdapter(
      ProductJpaRepository repository,
      ProductPersistenceMapper mapper,
      EntityManager entityManager) {
    this.repository = repository;
    this.mapper = mapper;
    this.entityManager = entityManager;
  }

  @Override
  public Optional<Product> findById(UUID productId) {
    return repository.findById(productId).map(mapper::toDomain);
  }

  @Override
  public List<Product> findAllById(Collection<UUID> productIds) {
    return repository.findAllById(productIds).stream().map(mapper::toDomain).toList();
  }

  @Override
  public boolean existsByCode(ProductCode code) {
    return repository.existsByCode(code.value());
  }

  @Override
  public PageResult<Product> search(ProductSearch query) {
    PageRequest pageable = PageRequest.of(query.page(), Math.min(query.size(), 100), sort(query));
    Page<Product> result = repository.findAll(matches(query), pageable).map(mapper::toDomain);
    return new PageResult<>(
        result.getContent(), result.getNumber(), result.getSize(), result.getTotalElements());
  }

  private static Sort sort(ProductSearch query) {
    if (query.sortBy() == null || query.sortBy().isBlank()) {
      return Sort.by("code");
    }
    Sort requested = Sort.by(query.ascending() ? Direction.ASC : Direction.DESC, query.sortBy());
    return query.sortBy().equals("code") ? requested : requested.and(Sort.by("code"));
  }

  private static Specification<ProductJpaEntity> matches(ProductSearch query) {
    return (product, criteria, builder) -> {
      List<Predicate> filters = new ArrayList<>();
      if (query.active() != null) {
        filters.add(builder.equal(product.get("active"), query.active()));
      }
      if (query.search() != null && !query.search().isBlank()) {
        String pattern =
            "%" + query.search().replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
        HibernateCriteriaBuilder hibernate = (HibernateCriteriaBuilder) builder;
        filters.add(
            hibernate.or(
                hibernate.ilike(product.get("code"), pattern, '!'),
                hibernate.ilike(product.get("description"), pattern, '!')));
      }
      return builder.and(filters);
    };
  }

  @Override
  public Product save(Product product) {
    ProductJpaEntity entity = entityManager.find(ProductJpaEntity.class, product.getId());
    long currentVersion = entity == null ? 0 : entity.getVersion();
    if (product.getVersion() != currentVersion) {
      throw new ObjectOptimisticLockingFailureException(ProductJpaEntity.class, product.getId());
    }
    if (entity == null) {
      entity = mapper.toEntity(product);
      entityManager.persist(entity);
    } else {
      mapper.updateEntity(product, entity);
    }
    // Flush checks @Version in the database and makes the resulting version available to the
    // caller.
    entityManager.flush();
    return mapper.toDomain(entity);
  }
}
