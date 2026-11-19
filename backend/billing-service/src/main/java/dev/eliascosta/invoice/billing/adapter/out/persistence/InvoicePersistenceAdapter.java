package dev.eliascosta.invoice.billing.adapter.out.persistence;

import dev.eliascosta.invoice.billing.domain.model.Invoice;
import dev.eliascosta.invoice.billing.domain.model.InvoiceItem;
import dev.eliascosta.invoice.billing.domain.port.PageResult;
import dev.eliascosta.invoice.billing.domain.port.out.InvoiceRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.Predicate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Repository;

@Repository
class InvoicePersistenceAdapter implements InvoiceRepository {

  private static final Sort DEFAULT_SORT = Sort.by(Direction.DESC, "number");

  private final InvoiceJpaRepository repository;
  private final InvoicePersistenceMapper mapper;
  private final EntityManager entityManager;

  InvoicePersistenceAdapter(
      InvoiceJpaRepository repository,
      InvoicePersistenceMapper mapper,
      EntityManager entityManager) {
    this.repository = repository;
    this.mapper = mapper;
    this.entityManager = entityManager;
  }

  @Override
  public Optional<Invoice> findById(UUID id) {
    return repository.findById(id).map(mapper::toDomain);
  }

  @Override
  public Invoice save(Invoice invoice) {
    InvoiceJpaEntity entity = entityManager.find(InvoiceJpaEntity.class, invoice.getId());
    if (entity == null) {
      if (invoice.getNumber() != null || invoice.getVersion() != 0) {
        throw new ObjectOptimisticLockingFailureException(InvoiceJpaEntity.class, invoice.getId());
      }
      entity = mapper.toEntity(invoice);
      addItems(invoice, entity);
      appendHistory(invoice, entity);
      entityManager.persist(entity);
    } else {
      if (entity.getVersion() != invoice.getVersion() || invoice.getNumber() == null) {
        throw new ObjectOptimisticLockingFailureException(InvoiceJpaEntity.class, invoice.getId());
      }
      mapper.updateEntity(invoice, entity);
      if (itemsChanged(invoice, entity)) {
        entity.getItems().clear();
        // Release the unique product and line-order keys before reusing them.
        entityManager.flush();
        addItems(invoice, entity);
      }
      appendHistory(invoice, entity);
    }

    entityManager.flush();
    return mapper.toDomain(entity);
  }

  private void addItems(Invoice invoice, InvoiceJpaEntity entity) {
    invoice.getItems().forEach(item -> entity.addItem(mapper.toEntity(item)));
  }

  private static boolean itemsChanged(Invoice invoice, InvoiceJpaEntity entity) {
    List<UUID> incoming = invoice.getItems().stream().map(InvoiceItem::getId).toList();
    List<UUID> stored = entity.getItems().stream().map(InvoiceItemJpaEntity::getId).toList();
    return !incoming.equals(stored);
  }

  private void appendHistory(Invoice invoice, InvoiceJpaEntity entity) {
    var existingIds =
        entity.getHistory().stream()
            .map(InvoiceStatusHistoryJpaEntity::getId)
            .collect(Collectors.toSet());
    invoice.getHistory().stream()
        .filter(entry -> !existingIds.contains(entry.getId()))
        .forEach(entry -> entity.addHistory(mapper.toEntity(entry)));
  }

  @Override
  public PageResult<Summary> search(Search query) {
    PageRequest pageable = PageRequest.of(query.page(), Math.min(query.size(), 100), sort(query));
    Page<Summary> page =
        repository.findBy(matches(query), rows -> rows.as(Summary.class).page(pageable));
    return new PageResult<>(
        page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements());
  }

  private static Sort sort(Search query) {
    if (query.sortBy() == null || query.sortBy().isBlank()) {
      return DEFAULT_SORT;
    }
    Sort requested = Sort.by(query.ascending() ? Direction.ASC : Direction.DESC, query.sortBy());
    return query.sortBy().equals("number") ? requested : requested.and(DEFAULT_SORT);
  }

  private static Specification<InvoiceJpaEntity> matches(Search query) {
    return (invoice, criteria, builder) -> {
      List<Predicate> filters = new ArrayList<>();
      if (query.status() != null) {
        filters.add(builder.equal(invoice.get("status"), query.status()));
      }
      if (query.customerId() != null) {
        filters.add(builder.equal(invoice.get("customerId"), query.customerId()));
      }
      var createdAt = invoice.<Instant>get("createdAt");
      if (query.from() != null) {
        filters.add(builder.greaterThanOrEqualTo(createdAt, query.from()));
      }
      if (query.to() != null) {
        filters.add(builder.lessThan(createdAt, query.to()));
      }
      return builder.and(filters);
    };
  }
}
