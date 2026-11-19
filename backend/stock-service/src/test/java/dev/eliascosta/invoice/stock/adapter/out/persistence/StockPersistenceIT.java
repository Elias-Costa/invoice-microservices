package dev.eliascosta.invoice.stock.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.eliascosta.invoice.stock.domain.model.Money;
import dev.eliascosta.invoice.stock.domain.model.MovementType;
import dev.eliascosta.invoice.stock.domain.model.Product;
import dev.eliascosta.invoice.stock.domain.model.ProductCode;
import dev.eliascosta.invoice.stock.domain.model.Quantity;
import dev.eliascosta.invoice.stock.domain.model.StockMovement;
import dev.eliascosta.invoice.stock.domain.port.ProductSearch;
import dev.eliascosta.invoice.stock.domain.port.out.ProductRepository;
import dev.eliascosta.invoice.stock.domain.port.out.StockMovementRepository;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.PersistenceExceptionTranslationAutoConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@DataJpaTest(showSql = false)
@ImportAutoConfiguration({
  FlywayAutoConfiguration.class,
  PersistenceExceptionTranslationAutoConfiguration.class
})
@Import({
  ProductPersistenceAdapter.class,
  ProductPersistenceMapperImpl.class,
  StockMovementPersistenceAdapter.class,
  StockMovementPersistenceMapperImpl.class
})
@ActiveProfiles("test")
@Testcontainers
class StockPersistenceIT {

  private static final Instant CREATED_AT = Instant.parse("2026-09-13T10:00:00Z");
  private static final Instant UPDATED_AT = CREATED_AT.plusSeconds(60);

  @Container @ServiceConnection
  static final PostgreSQLContainer postgres =
      new PostgreSQLContainer("postgres:16-alpine").withDatabaseName("stock_db");

  @Autowired private ProductRepository products;
  @Autowired private StockMovementRepository movements;
  @Autowired private EntityManager entityManager;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private PlatformTransactionManager transactionManager;

  @Test
  void restoresProductsAndSupportsCodeAndBatchLookups() {
    Product first = products.save(create(" bolt-1 ", "Parafuso", "12.35", 24).product());
    Product second = products.save(create("BOLT-2", "Porca", "2.99", 5).product());
    UUID missingId = UUID.randomUUID();
    first.changePrice(new Money(new BigDecimal("14.90")), "operator", UPDATED_AT);
    Product saved = products.save(first);
    entityManager.clear();

    assertThat(saved.getVersion()).isEqualTo(1);
    assertThat(products.findById(first.getId()))
        .hasValueSatisfying(
            restored -> assertThat(restored).usingRecursiveComparison().isEqualTo(saved));
    assertThat(products.existsByCode(new ProductCode(" bolt-1 "))).isTrue();
    assertThat(products.existsByCode(new ProductCode("MISSING"))).isFalse();
    assertThat(products.findById(missingId)).isEmpty();
    assertThat(products.findAllById(List.of(second.getId(), missingId, first.getId())))
        .extracting(Product::getId)
        .containsExactlyInAnyOrder(first.getId(), second.getId());
    assertThat(products.findAllById(List.of())).isEmpty();
  }

  @Test
  void searchesByCodeOrDescriptionAndPaginatesWithAnActiveFilter() {
    Product byCode = products.save(create("A-PAR", "Fixador", "7.00", 6).product());
    Product byDescription = products.save(create("B-FIX", "Parafuso de aço", "9.00", 3).product());
    Product inactive = create("C-PAR", "Parafuso antigo", "1.00", 1).product();
    inactive.deactivate("operator", UPDATED_AT);
    products.save(inactive);
    products.save(create("D-PORCA", "Porca", "3.00", 5).product());
    entityManager.clear();

    var first = products.search(new ProductSearch("pAr", true, 0, 1, "code", true));
    var second = products.search(new ProductSearch("pAr", true, 1, 1, "code", true));
    var pastEnd = products.search(new ProductSearch("pAr", true, 2, 1, "code", true));

    assertThat(first.content()).extracting(Product::getId).containsExactly(byCode.getId());
    assertThat(first.number()).isZero();
    assertThat(first.size()).isEqualTo(1);
    assertThat(first.totalElements()).isEqualTo(2);
    assertThat(first.totalPages()).isEqualTo(2);
    assertThat(second.content()).extracting(Product::getId).containsExactly(byDescription.getId());
    assertThat(second.number()).isEqualTo(1);
    assertThat(pastEnd.content()).isEmpty();
    assertThat(pastEnd.totalElements()).isEqualTo(2);
    assertThat(products.search(new ProductSearch("par", false, 0, 20, null, true)).content())
        .extracting(Product::getId)
        .containsExactly(inactive.getId());
    assertThat(products.search(new ProductSearch("par", null, 0, 20, "unitPrice", false)).content())
        .extracting(Product::getId)
        .containsExactly(byDescription.getId(), byCode.getId(), inactive.getId());
  }

  @Test
  void matchesSearchPunctuationLiterallyAndCapsPageSize() {
    Product literal = products.save(create("LITERAL-1", "Kit 50%_!", "5.00", 1).product());
    products.save(create("LITERAL-2", "Kit 50abcX!", "5.00", 1).product());

    var page = products.search(new ProductSearch("50%_!", null, 0, 101, null, true));

    assertThat(page.size()).isEqualTo(100);
    assertThat(page.content()).extracting(Product::getId).containsExactly(literal.getId());
  }

  @Test
  void paginatesImmutableHistoryFromMostRecentMovement() {
    Product.Creation creation = create("HISTORY", "Histórico", "12.50", 0);
    Product product = products.save(creation.product());
    StockMovement inbound = product.credit(new Quantity(7), "Compra", "operator", UPDATED_AT);
    StockMovement invoiceDebit =
        product.debitForInvoice(
            new Quantity(2), UUID.randomUUID(), 123L, "billing", UPDATED_AT.plusSeconds(1));
    products.save(product);
    movements.append(creation.initialMovement());
    movements.append(inbound);
    movements.append(invoiceDebit);
    Product.Creation other = create("OTHER-HISTORY", "Outro produto", "2.70", 3);
    products.save(other.product());
    movements.append(other.initialMovement());
    entityManager.flush();
    entityManager.clear();

    var latest = movements.findByProductId(product.getId(), 0, 2);
    var earlier = movements.findByProductId(product.getId(), 1, 2);
    assertThat(latest.totalElements()).isEqualTo(3);
    assertThat(latest.totalPages()).isEqualTo(2);
    assertThat(latest.content())
        .usingRecursiveFieldByFieldElementComparator()
        .containsExactly(invoiceDebit, inbound);
    assertThat(earlier.content())
        .usingRecursiveFieldByFieldElementComparator()
        .containsExactly(creation.initialMovement());
    assertThat(
            jdbc.queryForObject(
                "SELECT type FROM stock_movements WHERE id = ?",
                String.class,
                invoiceDebit.getId()))
        .isEqualTo(MovementType.INVOICE_DEBIT.name());
  }

  @Test
  void rejectsDuplicateNormalizedCodes() {
    products.save(create("UNIQUE", "Primeiro", "1.50", 0).product());

    assertThatThrownBy(() -> products.save(create(" unique ", "Segundo", "1.50", 0).product()))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasStackTraceContaining("uk_products_code");
  }

  @Test
  void rejectsNegativeBalanceAtTheDatabase() {
    Product product = products.save(create("BALANCE", "Saldo", "5.00", 3).product());

    assertThatThrownBy(
            () -> jdbc.update("UPDATE products SET balance = -1 WHERE id = ?", product.getId()))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasStackTraceContaining("ck_products_balance");
  }

  @Test
  void rejectsASecondInvoiceMovementForTheSameInvoiceAndProduct() {
    Product product = products.save(create("INVOICE", "Baixa", "5.00", 5).product());
    UUID invoiceId = UUID.randomUUID();
    movements.append(
        product.debitForInvoice(new Quantity(1), invoiceId, 123L, "billing", UPDATED_AT));
    entityManager.flush();
    movements.append(
        product.debitForInvoice(
            new Quantity(1), invoiceId, 123L, "billing", UPDATED_AT.plusSeconds(1)));

    assertThatThrownBy(entityManager::flush)
        .isInstanceOf(ConstraintViolationException.class)
        .hasStackTraceContaining("ux_stock_movements_invoice_product");
  }

  @Test
  void rejectsAnOutdatedDomainSnapshotInTheSameTransaction() {
    Product original = products.save(create("STALE", "Original", "5.00", 2).product());
    Product outdated = products.findById(original.getId()).orElseThrow();
    original.changeDescription("Atualizado", "winner", UPDATED_AT);
    products.save(original);
    outdated.changeDescription("Desatualizado", "loser", UPDATED_AT);

    assertThatThrownBy(() -> products.save(outdated))
        .isInstanceOf(OptimisticLockingFailureException.class);
  }

  @Test
  void rejectsTheSecondWriteWhenBothTransactionsLoadedTheSameVersion() {
    TransactionTemplate independent = new TransactionTemplate(transactionManager);
    independent.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    UUID id =
        independent.execute(
            status -> products.save(create("CONCURRENT", "Original", "5.00", 2).product()).getId());
    Product outdated = products.findById(id).orElseThrow();

    Product winner =
        independent.execute(
            status -> {
              Product current = products.findById(id).orElseThrow();
              assertThat(current.getVersion()).isEqualTo(outdated.getVersion());
              current.changeDescription("Gravação confirmada", "winner", UPDATED_AT);
              return products.save(current);
            });

    assertThat(winner.getVersion()).isEqualTo(1);
    outdated.changeDescription("Gravação concorrente", "loser", UPDATED_AT.plusSeconds(1));
    assertThatThrownBy(() -> products.save(outdated))
        .isInstanceOf(OptimisticLockingFailureException.class);
    Product restored = independent.execute(status -> products.findById(id).orElseThrow());
    assertThat(restored).usingRecursiveComparison().isEqualTo(winner);
  }

  private static Product.Creation create(
      String code, String description, String price, int balance) {
    return Product.create(
        new ProductCode(code),
        description,
        new Money(new BigDecimal(price)),
        new Quantity(balance),
        "tester",
        CREATED_AT);
  }
}
