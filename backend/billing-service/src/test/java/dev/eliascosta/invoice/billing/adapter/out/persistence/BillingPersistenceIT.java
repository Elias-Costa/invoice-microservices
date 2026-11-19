package dev.eliascosta.invoice.billing.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.eliascosta.invoice.billing.domain.model.Customer;
import dev.eliascosta.invoice.billing.domain.model.Document;
import dev.eliascosta.invoice.billing.domain.model.Email;
import dev.eliascosta.invoice.billing.domain.model.Invoice;
import dev.eliascosta.invoice.billing.domain.model.InvoiceItem;
import dev.eliascosta.invoice.billing.domain.model.InvoiceStatus;
import dev.eliascosta.invoice.billing.domain.model.InvoiceStatusHistory;
import dev.eliascosta.invoice.billing.domain.model.Money;
import dev.eliascosta.invoice.billing.domain.model.Quantity;
import dev.eliascosta.invoice.billing.domain.port.out.CustomerRepository;
import dev.eliascosta.invoice.billing.domain.port.out.InvoiceRepository;
import dev.eliascosta.invoice.billing.domain.port.out.InvoiceRepository.Search;
import dev.eliascosta.invoice.billing.domain.port.out.InvoiceRepository.Summary;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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
  CustomerPersistenceAdapter.class,
  CustomerPersistenceMapperImpl.class,
  InvoicePersistenceAdapter.class,
  InvoicePersistenceMapperImpl.class
})
@ActiveProfiles("test")
@Testcontainers
class BillingPersistenceIT {

  private static final Instant CREATED_AT = Instant.parse("2026-11-19T10:00:00Z");
  private static final Instant UPDATED_AT = CREATED_AT.plusSeconds(60);
  private static final Document CPF = new Document("52998224725");
  private static final Document CNPJ = new Document("11222333000181");

  @Container @ServiceConnection
  static final PostgreSQLContainer postgres =
      new PostgreSQLContainer("postgres:16-alpine").withDatabaseName("billing_db");

  @Autowired private CustomerRepository customers;
  @Autowired private InvoiceRepository invoices;
  @Autowired private EntityManager entityManager;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private PlatformTransactionManager transactionManager;

  @Test
  void restoresCustomerWithDocumentAndUpdatedAuditFields() {
    Customer customer = customers.save(newCustomer(CPF));
    assertThat(customer.getEmail()).isEqualTo(new Email("cliente@example.com"));
    assertThat(customer.getVersion()).isZero();
    assertThat(customers.existsByDocument(CPF)).isTrue();
    assertThat(customers.existsByDocument(CNPJ)).isFalse();
    assertThat(
            jdbc.queryForObject(
                "select document_type from customers where id = ?", String.class, customer.getId()))
        .isEqualTo("CPF");

    customer.edit("Nome atualizado", null, null, "operator", UPDATED_AT);
    customer.deactivate("operator", UPDATED_AT);
    Customer saved = customers.save(customer);
    entityManager.clear();

    assertThat(saved.getVersion()).isEqualTo(1);
    assertThat(saved.getEmail()).isNull();
    assertThat(saved.getPhone()).isNull();
    assertThat(saved.isActive()).isFalse();
    assertThat(customers.findById(customer.getId()))
        .hasValueSatisfying(
            restored -> assertThat(restored).usingRecursiveComparison().isEqualTo(saved));
    assertThat(customers.findById(UUID.randomUUID())).isEmpty();
  }

  @Test
  void assignsNumbersFromDatabaseAndRestoresSnapshotsAndTransitions() {
    Customer customer = customers.save(newCustomer(CPF));
    InvoiceItem.Input firstItem = item();
    Invoice draft = draft(customer, CREATED_AT, firstItem, item());
    assertThat(draft.getNumber()).isNull();
    Invoice first = invoices.save(draft);
    Invoice second = invoices.save(draft(customer, CREATED_AT, item()));
    Long storedNumber =
        jdbc.queryForObject("select number from invoices where id = ?", Long.class, first.getId());

    assertThat(first.getNumber()).isPositive().isEqualTo(storedNumber);
    assertThat(second.getNumber()).isEqualTo(first.getNumber() + 1);
    assertThat(first.getVersion()).isZero();

    customer.edit("Cliente atualizado", null, null, "operator", UPDATED_AT);
    customers.save(customer);
    UUID requestId = first.requestPrint("operator", UPDATED_AT);
    Invoice processing = invoices.save(first);
    entityManager.clear();
    Invoice closing = invoices.findById(first.getId()).orElseThrow();
    assertThat(closing).usingRecursiveComparison().isEqualTo(processing);
    assertThat(closing.getProcessingStartedAt()).isEqualTo(UPDATED_AT);
    assertThat(closing.getPrintRequestId()).isEqualTo(requestId);
    closing.confirmClose(UPDATED_AT.plusSeconds(1));
    Invoice closed = invoices.save(closing);
    entityManager.clear();

    Invoice restored = invoices.findById(first.getId()).orElseThrow();
    assertThat(restored).usingRecursiveComparison().isEqualTo(closed);
    assertThat(restored.getNumber()).isEqualTo(storedNumber);
    assertThat(restored.getCustomerName()).isEqualTo("Cliente original");
    assertThat(restored.getCustomerDocument()).isEqualTo(CPF);
    assertThat(restored.getItems()).extracting(InvoiceItem::getLineOrder).containsExactly(1, 2);
    assertThat(restored.getItems().getFirst().getProductId()).isEqualTo(firstItem.productId());
    assertThat(restored.getTotal().value()).isEqualByComparingTo("60.00");
    assertThat(restored.getHistory())
        .extracting(InvoiceStatusHistory::getToStatus)
        .containsExactly(InvoiceStatus.OPEN, InvoiceStatus.PROCESSING, InvoiceStatus.CLOSED);
    assertThat(restored.getProcessingStartedAt()).isNull();
    assertThat(restored.getPrintRequestId()).isEqualTo(requestId);
    assertThat(invoices.findById(UUID.randomUUID())).isEmpty();
  }

  @Test
  void replacesItemsSharingProductAndOrderKeysAndPreservesHistory() {
    Customer customer = customers.save(newCustomer(CPF));
    InvoiceItem.Input kept = item();
    Invoice invoice = invoices.save(draft(customer, CREATED_AT, item(), kept));
    var initialHistory = invoice.getHistory();

    invoice.replaceItems(List.of(kept), "operator", UPDATED_AT);
    Invoice edited = invoices.save(invoice);
    UUID previousItemId = edited.getItems().getFirst().getId();
    Invoice stale = invoices.findById(invoice.getId()).orElseThrow();
    edited.replaceItems(List.of(kept), "operator", UPDATED_AT);
    Invoice replaced = invoices.save(edited);
    entityManager.clear();

    Invoice restored = invoices.findById(invoice.getId()).orElseThrow();
    assertThat(restored).usingRecursiveComparison().isEqualTo(replaced);
    assertThat(restored.getNumber()).isEqualTo(invoice.getNumber());
    assertThat(restored.getVersion()).isGreaterThan(stale.getVersion());
    assertThat(restored.getTotal().value()).isEqualByComparingTo("30.00");
    assertThat(restored.getHistory()).usingRecursiveComparison().isEqualTo(initialHistory);
    assertThat(restored.getItems())
        .singleElement()
        .satisfies(
            item -> {
              assertThat(item.getId()).isNotEqualTo(previousItemId);
              assertThat(item.getProductId()).isEqualTo(kept.productId());
              assertThat(item.getLineOrder()).isEqualTo(1);
            });
    assertThat(
            jdbc.queryForList(
                "select id from invoice_items where invoice_id = ?", UUID.class, invoice.getId()))
        .containsExactly(restored.getItems().getFirst().getId());

    stale.cancel("Revisão", "operator", UPDATED_AT.plusSeconds(1));
    assertThatThrownBy(() -> invoices.save(stale))
        .isInstanceOf(OptimisticLockingFailureException.class);
  }

  @Test
  void detectsConcurrentRootUpdatesInTheDatabase() {
    TransactionTemplate independent = new TransactionTemplate(transactionManager);
    independent.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    UUID invoiceId =
        independent.execute(
            tx -> {
              Customer customer = customers.save(newCustomer(new Document("04252011000110")));
              return invoices.save(draft(customer, CREATED_AT, item())).getId();
            });
    Invoice stale = invoices.findById(invoiceId).orElseThrow();

    independent.executeWithoutResult(
        tx -> {
          Invoice winner = invoices.findById(invoiceId).orElseThrow();
          winner.requestPrint("operator", UPDATED_AT);
          invoices.save(winner);
        });

    stale.cancel("Revisão", "operator", UPDATED_AT.plusSeconds(1));
    assertThatThrownBy(() -> invoices.save(stale))
        .isInstanceOf(OptimisticLockingFailureException.class);
    independent.executeWithoutResult(
        tx ->
            assertThat(invoices.findById(invoiceId))
                .hasValueSatisfying(
                    restored ->
                        assertThat(restored.getStatus()).isEqualTo(InvoiceStatus.PROCESSING)));
  }

  @Test
  void filtersByStatusCustomerAndHalfOpenPeriodWithPagination() {
    Customer customer = customers.save(newCustomer(CPF));
    Customer otherCustomer = customers.save(newCustomer(CNPJ));
    Instant from = CREATED_AT.plusSeconds(20);
    Instant to = CREATED_AT.plusSeconds(40);
    Invoice before = invoices.save(draft(customer, CREATED_AT, item()));
    Invoice first = invoices.save(draft(customer, from, item()));
    Invoice second = invoices.save(draft(customer, from.plusSeconds(10), item()));
    Invoice canceled = invoices.save(draft(customer, from, item()));
    canceled.cancel("Revisão", "operator", UPDATED_AT);
    invoices.save(canceled);
    invoices.save(draft(otherCustomer, from, item()));
    Invoice upperBound = invoices.save(draft(customer, to, item()));
    entityManager.clear();

    var page1 = invoices.search(search(customer, from, to, 0));
    var page2 = invoices.search(search(customer, from, to, 1));
    var emptyPage = invoices.search(search(customer, from, to, 2));

    assertThat(page1.content()).extracting(Summary::id).containsExactly(second.getId());
    assertThat(page1.content().getFirst().customerName()).isEqualTo(customer.getName());
    assertThat(page1.content().getFirst().total()).isEqualByComparingTo("30.00");
    assertThat(page1.number()).isZero();
    assertThat(page1.size()).isEqualTo(1);
    assertThat(page1.totalElements()).isEqualTo(2);
    assertThat(page1.totalPages()).isEqualTo(2);
    assertThat(page2.content()).extracting(Summary::id).containsExactly(first.getId());
    assertThat(emptyPage.content()).isEmpty();
    assertThat(emptyPage.totalElements()).isEqualTo(2);

    var lowerOnly =
        invoices.search(
            new Search(
                InvoiceStatus.OPEN, customer.getId(), from, null, 0, 101, "createdAt", true));
    assertThat(lowerOnly.size()).isEqualTo(100);
    assertThat(lowerOnly.content())
        .extracting(Summary::id)
        .containsExactly(first.getId(), second.getId(), upperBound.getId());
    var upperOnly =
        invoices.search(
            new Search(InvoiceStatus.OPEN, customer.getId(), null, from, 0, 20, null, false));
    assertThat(upperOnly.content()).extracting(Summary::id).containsExactly(before.getId());
    var noPeriod =
        invoices.search(new Search(null, customer.getId(), null, null, 0, 20, null, false));
    assertThat(noPeriod.content())
        .extracting(Summary::id)
        .containsExactly(
            upperBound.getId(), canceled.getId(), second.getId(), first.getId(), before.getId());
  }

  @Test
  void rejectsTwoItemsOfTheSameProductInTheDatabase() {
    Customer customer = customers.save(newCustomer(CPF));
    InvoiceItem.Input product = item();
    Invoice invoice = invoices.save(draft(customer, CREATED_AT, product));

    assertConstraint(
        "uk_invoice_items_invoice_product",
        () -> insertItem(invoice.getId(), product.productId(), new BigDecimal("30.00")));
  }

  @Test
  void rejectsAnIncorrectLineTotalInTheDatabase() {
    Customer customer = customers.save(newCustomer(CPF));
    Invoice invoice = invoices.save(draft(customer, CREATED_AT, item()));

    assertConstraint(
        "ck_invoice_items_line_total",
        () -> insertItem(invoice.getId(), UUID.randomUUID(), new BigDecimal("29.99")));
  }

  @ParameterizedTest
  @CsvSource(
      delimiter = '|',
      textBlock =
          """
          status = 'UNKNOWN'                            | ck_invoices_status
          total = -0.01                                 | ck_invoices_total
          closed_at = created_at                        | ck_invoices_closed
          canceled_at = created_at                      | ck_invoices_canceled
          cancel_reason = 'Revisão'                      | ck_invoices_cancel_reason
          status = 'PROCESSING', processing_started_at = created_at | ck_invoices_processing
          status = 'PROCESSING', current_print_request_id = id      | ck_invoices_processing
          """)
  void rejectsIncoherentInvoiceRows(String update, String constraint) {
    Customer customer = customers.save(newCustomer(CPF));
    Invoice invoice = invoices.save(draft(customer, CREATED_AT, item()));

    assertConstraint(
        constraint,
        () -> jdbc.update("update invoices set " + update + " where id = ?", invoice.getId()));
  }

  private void insertItem(UUID invoiceId, UUID productId, BigDecimal total) {
    jdbc.update(
        """
        insert into invoice_items
          (id, invoice_id, product_id, product_code, product_description,
           unit_price, quantity, line_total, line_order)
        values (?, ?, ?, 'ITEM', 'Produto', 10.00, 3, ?, 2)
        """,
        UUID.randomUUID(),
        invoiceId,
        productId,
        total);
  }

  private static void assertConstraint(String constraint, Runnable action) {
    assertThatThrownBy(action::run)
        .isInstanceOfSatisfying(
            DataIntegrityViolationException.class,
            error -> assertThat(error.getMostSpecificCause()).hasMessageContaining(constraint));
  }

  private static Customer newCustomer(Document document) {
    return Customer.create(
        "Cliente original",
        document,
        new Email("cliente@example.com"),
        "5585999990000",
        "operator",
        CREATED_AT);
  }

  private static Invoice draft(Customer customer, Instant createdAt, InvoiceItem.Input... items) {
    return Invoice.create(customer, List.of(items), "operator", createdAt);
  }

  private static InvoiceItem.Input item() {
    return new InvoiceItem.Input(
        UUID.randomUUID(), "ITEM", "Produto", new Money(new BigDecimal("10.00")), new Quantity(3));
  }

  private static Search search(Customer customer, Instant from, Instant to, int page) {
    return new Search(InvoiceStatus.OPEN, customer.getId(), from, to, page, 1, null, false);
  }
}
