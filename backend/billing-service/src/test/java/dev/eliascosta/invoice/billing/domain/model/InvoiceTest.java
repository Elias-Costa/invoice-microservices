package dev.eliascosta.invoice.billing.domain.model;

import static dev.eliascosta.invoice.billing.domain.model.InvoiceStatus.CANCELED;
import static dev.eliascosta.invoice.billing.domain.model.InvoiceStatus.CLOSED;
import static dev.eliascosta.invoice.billing.domain.model.InvoiceStatus.OPEN;
import static dev.eliascosta.invoice.billing.domain.model.InvoiceStatus.PROCESSING;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import dev.eliascosta.invoice.billing.domain.exception.CustomerInactiveException;
import dev.eliascosta.invoice.billing.domain.exception.DuplicateInvoiceItemException;
import dev.eliascosta.invoice.billing.domain.exception.InvalidInvoiceStatusException;
import dev.eliascosta.invoice.common.error.InvalidRequestException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class InvoiceTest {

  private static final Instant CREATED_AT = Instant.parse("2026-09-12T10:00:00Z");
  private static final Instant CHANGED_AT = CREATED_AT.plusSeconds(60);
  private static final String OPERATOR = "operator";
  private static final String ADMIN = "admin";
  private static final Document DOCUMENT = new Document("52998224725");
  private static final String FAILURE = "Saldo insuficiente para PAR-M6-20";
  private static final UUID PRODUCT_ID = UUID.fromString("c0000000-0000-4000-8000-000000000001");

  @Test
  void createsOpenInvoiceWithSnapshotsAndCalculatedTotals() {
    Customer customer = newCustomer();
    InvoiceItem.Input fasteners = item(PRODUCT_ID, "PAR-M6-20", "Parafuso M6", "0.345", 3);
    InvoiceItem.Input nuts = item(UUID.randomUUID(), "POR-M6", "Porca M6", "0.206", 2);
    var inputs = new ArrayList<>(List.of(fasteners, nuts));

    Invoice invoice = Invoice.create(customer, inputs, OPERATOR, CREATED_AT);
    customer.edit("Ana Paula Ribeiro", null, null, ADMIN, CHANGED_AT);
    inputs.clear();

    assertThat(invoice.getId()).isNotNull();
    assertThat(invoice.getNumber()).isNull();
    assertThat(invoice.getStatus()).isEqualTo(OPEN);
    assertThat(invoice.getCustomerId()).isEqualTo(customer.getId());
    assertThat(invoice.getCustomerName()).isEqualTo("Ana Ribeiro");
    assertThat(invoice.getCustomerDocument()).isEqualTo(DOCUMENT);
    assertThat(invoice.getVersion()).isZero();
    assertThat(invoice.getCreatedBy()).isEqualTo(OPERATOR);
    assertThat(invoice.getCreatedAt()).isEqualTo(CREATED_AT);
    assertThat(invoice.getUpdatedBy()).isNull();
    assertThat(invoice.getUpdatedAt()).isNull();
    assertThat(invoice.getItems())
        .extracting(
            InvoiceItem::getProductId,
            InvoiceItem::getProductCode,
            InvoiceItem::getProductDescription,
            InvoiceItem::getUnitPrice,
            InvoiceItem::getQuantity,
            InvoiceItem::getLineTotal,
            InvoiceItem::getLineOrder)
        .containsExactly(
            tuple(
                PRODUCT_ID,
                "PAR-M6-20",
                "Parafuso M6",
                money("0.35"),
                new Quantity(3),
                money("1.05"),
                1),
            tuple(
                nuts.productId(),
                "POR-M6",
                "Porca M6",
                money("0.21"),
                new Quantity(2),
                money("0.42"),
                2));
    assertThat(invoice.getTotal()).isEqualTo(money("1.47"));
    assertThat(invoice.getItems())
        .extracting(InvoiceItem::getId)
        .doesNotContainNull()
        .doesNotHaveDuplicates();
    assertThatThrownBy(() -> invoice.getItems().clear())
        .isInstanceOf(UnsupportedOperationException.class);
    assertHistory(invoice, tuple(null, OPEN, null, OPERATOR, CREATED_AT));
  }

  @Test
  void rejectsInvoiceForInactiveCustomer() {
    Customer customer = newCustomer();
    customer.deactivate(ADMIN, CREATED_AT);

    assertThatThrownBy(
            () -> Invoice.create(customer, List.of(originalItem()), OPERATOR, CREATED_AT))
        .isInstanceOf(CustomerInactiveException.class);
  }

  @Test
  void requiresAtLeastOneItemOnCreationAndReplacement() {
    assertViolation("items", () -> Invoice.create(newCustomer(), List.of(), OPERATOR, CREATED_AT));
    Invoice invoice = newInvoice();

    assertViolation("items", () -> invoice.replaceItems(List.of(), OPERATOR, CHANGED_AT));

    assertThat(invoice.getItems()).hasSize(1);
    assertThat(invoice.getTotal()).isEqualTo(money("1.05"));
    assertThat(invoice.getUpdatedAt()).isNull();
  }

  @ParameterizedTest
  @ValueSource(ints = {0, -1})
  void requiresPositiveQuantity(int value) {
    assertViolation("quantity", () -> new Quantity(value));
  }

  @Test
  void rejectsDuplicateProductOnCreationAndReplacement() {
    InvoiceItem.Input original = originalItem();
    List<InvoiceItem.Input> duplicate =
        List.of(original, item(original.productId(), "PAR-M6-20", "Parafuso M6", "0.35", 5));
    Invoice invoice = newInvoice();

    assertThatThrownBy(() -> Invoice.create(newCustomer(), duplicate, OPERATOR, CREATED_AT))
        .isInstanceOfSatisfying(
            DuplicateInvoiceItemException.class,
            error -> assertThat(error.getType()).isEqualTo("duplicate-invoice-item"));
    assertThatThrownBy(() -> invoice.replaceItems(duplicate, OPERATOR, CHANGED_AT))
        .isInstanceOf(DuplicateInvoiceItemException.class);

    assertThat(invoice.getItems()).hasSize(1);
    assertThat(invoice.getTotal()).isEqualTo(money("1.05"));
    assertThat(invoice.getUpdatedAt()).isNull();
  }

  @Test
  void replacesItemsInOpenInvoiceAndPreservesOriginalCustomerSnapshot() {
    Customer customer = newCustomer();
    Invoice invoice = Invoice.create(customer, List.of(originalItem()), OPERATOR, CREATED_AT);
    InvoiceItem previous = invoice.getItems().getFirst();
    customer.edit("Ana Paula Ribeiro", null, null, ADMIN, CHANGED_AT);
    customer.deactivate(ADMIN, CHANGED_AT);

    invoice.replaceItems(
        List.of(item(PRODUCT_ID, "PAR-M6-20", "Nova descrição", "0.50", 4)), ADMIN, CHANGED_AT);

    assertThat(invoice.getStatus()).isEqualTo(OPEN);
    assertThat(invoice.getItems())
        .singleElement()
        .satisfies(
            replacement -> {
              assertThat(replacement.getId()).isNotEqualTo(previous.getId());
              assertThat(replacement.getProductId()).isEqualTo(previous.getProductId());
              assertThat(replacement.getProductDescription()).isEqualTo("Nova descrição");
              assertThat(replacement.getUnitPrice()).isEqualTo(money("0.50"));
              assertThat(replacement.getQuantity()).isEqualTo(new Quantity(4));
              assertThat(replacement.getLineOrder()).isEqualTo(1);
            });
    assertThat(invoice.getCustomerName()).isEqualTo("Ana Ribeiro");
    assertThat(invoice.getCustomerDocument()).isEqualTo(DOCUMENT);
    assertThat(invoice.getTotal()).isEqualTo(money("2.00"));
    assertThat(invoice.getUpdatedBy()).isEqualTo(ADMIN);
    assertThat(invoice.getUpdatedAt()).isEqualTo(CHANGED_AT);
    assertHistory(invoice, tuple(null, OPEN, null, OPERATOR, CREATED_AT));
  }

  @Test
  void rejectsLineAndInvoiceTotalOverflowBeforeMutatingInvoice() {
    Invoice invoice = newInvoice();
    InvoiceItem previous = invoice.getItems().getFirst();
    var overlargeLine = List.of(item(PRODUCT_ID, "A", "Produto A", "5000000000000.00", 2));
    var overlargeInvoice =
        List.of(
            item(PRODUCT_ID, "A", "Produto A", "9999999999999.99", 1),
            item(UUID.randomUUID(), "B", "Produto B", "0.01", 1));

    assertViolation(
        "lineTotal", () -> Invoice.create(newCustomer(), overlargeLine, OPERATOR, CREATED_AT));
    assertViolation(
        "total", () -> Invoice.create(newCustomer(), overlargeInvoice, OPERATOR, CREATED_AT));
    assertViolation("lineTotal", () -> invoice.replaceItems(overlargeLine, ADMIN, CHANGED_AT));
    assertViolation("total", () -> invoice.replaceItems(overlargeInvoice, ADMIN, CHANGED_AT));

    assertThat(invoice.getItems()).containsExactly(previous);
    assertThat(invoice.getTotal()).isEqualTo(money("1.05"));
    assertThat(invoice.getUpdatedBy()).isNull();
    assertThat(invoice.getUpdatedAt()).isNull();
    assertHistory(invoice, tuple(null, OPEN, null, OPERATOR, CREATED_AT));
  }

  @Test
  void acceptsZeroAndLargestRepresentableTotals() {
    var freeItem = item(PRODUCT_ID, "A", "Produto A", "0.004", 1);
    var maximumItem = item(UUID.randomUUID(), "B", "Produto B", "9999999999999.994", 1);

    Invoice invoice =
        Invoice.create(newCustomer(), List.of(freeItem, maximumItem), OPERATOR, CREATED_AT);

    assertThat(invoice.getItems())
        .extracting(InvoiceItem::getLineTotal)
        .containsExactly(money("0.00"), money("9999999999999.99"));
    assertThat(invoice.getTotal()).isEqualTo(money("9999999999999.99"));
    assertViolation("unitPrice", () -> money("9999999999999.995"));
    assertViolation("unitPrice", () -> money("-0.001"));
  }

  @Test
  void requestsPrintAndClosesWithAudit() {
    Invoice invoice = newInvoice();
    Instant closedAt = CHANGED_AT.plusSeconds(60);
    List<InvoiceStatusHistory> originalHistory = invoice.getHistory();

    UUID requestId = invoice.requestPrint(OPERATOR, CHANGED_AT);

    assertThat(requestId).isNotNull();
    assertThat(invoice.getStatus()).isEqualTo(PROCESSING);
    assertThat(invoice.getPrintRequestId()).isEqualTo(requestId);
    assertThat(invoice.getProcessingStartedAt()).isEqualTo(CHANGED_AT);
    assertThat(invoice.getUpdatedBy()).isEqualTo(OPERATOR);
    assertThat(invoice.getUpdatedAt()).isEqualTo(CHANGED_AT);

    invoice.confirmClose(closedAt);

    assertThat(invoice.getStatus()).isEqualTo(CLOSED);
    assertThat(invoice.getClosedAt()).isEqualTo(closedAt);
    assertThat(invoice.getProcessingStartedAt()).isNull();
    assertThat(invoice.getPrintRequestId()).isEqualTo(requestId);
    assertThat(invoice.getUpdatedBy()).isEqualTo("system");
    assertThat(invoice.getUpdatedAt()).isEqualTo(closedAt);
    assertThat(originalHistory).hasSize(1);
    assertThatThrownBy(() -> invoice.getHistory().clear())
        .isInstanceOf(UnsupportedOperationException.class);
    assertHistory(
        invoice,
        tuple(null, OPEN, null, OPERATOR, CREATED_AT),
        tuple(OPEN, PROCESSING, null, OPERATOR, CHANGED_AT),
        tuple(PROCESSING, CLOSED, null, "system", closedAt));
  }

  @Test
  void reopensOnRejectionAndClearsFailureAfterSuccessfulRetry() {
    Invoice invoice = newInvoice();
    Instant rejectedAt = CHANGED_AT.plusSeconds(10);
    Instant retriedAt = CHANGED_AT.plusSeconds(20);
    Instant closedAt = CHANGED_AT.plusSeconds(30);
    UUID firstRequest = invoice.requestPrint(OPERATOR, CHANGED_AT);

    invoice.rejectClose(FAILURE, rejectedAt);

    assertThat(invoice.getStatus()).isEqualTo(OPEN);
    assertThat(invoice.getClosedAt()).isNull();
    assertThat(invoice.getLastFailureReason()).isEqualTo(FAILURE);
    assertThat(invoice.getPrintRequestId()).isEqualTo(firstRequest);
    assertThat(invoice.getProcessingStartedAt()).isNull();
    assertThat(invoice.getUpdatedBy()).isEqualTo("system");
    assertThat(invoice.getUpdatedAt()).isEqualTo(rejectedAt);

    UUID retry = invoice.requestPrint(OPERATOR, retriedAt);

    assertThat(retry).isNotEqualTo(firstRequest);
    assertThat(invoice.getLastFailureReason()).isEqualTo(FAILURE);
    assertThat(invoice.getProcessingStartedAt()).isEqualTo(retriedAt);

    invoice.confirmClose(closedAt);

    assertThat(invoice.getPrintRequestId()).isEqualTo(retry);
    assertThat(invoice.getLastFailureReason()).isNull();
    assertHistory(
        invoice,
        tuple(null, OPEN, null, OPERATOR, CREATED_AT),
        tuple(OPEN, PROCESSING, null, OPERATOR, CHANGED_AT),
        tuple(PROCESSING, OPEN, FAILURE, "system", rejectedAt),
        tuple(OPEN, PROCESSING, null, OPERATOR, retriedAt),
        tuple(PROCESSING, CLOSED, null, "system", closedAt));
  }

  @Test
  void cancelsOpenInvoiceAndRecordsReason() {
    Invoice invoice = newInvoice();
    String reason = "Pedido duplicado";

    invoice.cancel(reason, ADMIN, CHANGED_AT);

    assertThat(invoice.getStatus()).isEqualTo(CANCELED);
    assertThat(invoice.getCanceledAt()).isEqualTo(CHANGED_AT);
    assertThat(invoice.getCancelReason()).isEqualTo(reason);
    assertThat(invoice.getUpdatedBy()).isEqualTo(ADMIN);
    assertThat(invoice.getUpdatedAt()).isEqualTo(CHANGED_AT);
    assertThat(invoice.getClosedAt()).isNull();
    assertThat(invoice.getPrintRequestId()).isNull();
    assertThat(invoice.getItems()).hasSize(1);
    assertThat(invoice.getTotal()).isEqualTo(money("1.05"));
    assertHistory(
        invoice,
        tuple(null, OPEN, null, OPERATOR, CREATED_AT),
        tuple(OPEN, CANCELED, reason, ADMIN, CHANGED_AT));
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {" "})
  void requiresReasonsWithoutPartiallyChangingState(String reason) {
    Invoice invoice = newInvoice();

    assertViolation("reason", () -> invoice.cancel(reason, ADMIN, CHANGED_AT));
    assertViolation("reason", () -> invoice.cancel("x".repeat(501), ADMIN, CHANGED_AT));
    assertThat(invoice.getStatus()).isEqualTo(OPEN);
    assertThat(invoice.getCanceledAt()).isNull();
    assertThat(invoice.getUpdatedAt()).isNull();
    assertHistory(invoice, tuple(null, OPEN, null, OPERATOR, CREATED_AT));

    invoice.requestPrint(OPERATOR, CHANGED_AT);
    assertViolation("reason", () -> invoice.rejectClose(reason, CHANGED_AT.plusSeconds(60)));

    assertThat(invoice.getStatus()).isEqualTo(PROCESSING);
    assertThat(invoice.getLastFailureReason()).isNull();
    assertThat(invoice.getProcessingStartedAt()).isEqualTo(CHANGED_AT);
    assertThat(invoice.getUpdatedAt()).isEqualTo(CHANGED_AT);
    assertThat(invoice.getHistory()).hasSize(2);
  }

  @ParameterizedTest
  @EnumSource(
      value = InvoiceStatus.class,
      names = {"PROCESSING", "CLOSED", "CANCELED"})
  void allowsEditingPrintingAndCancellationOnlyWhileOpen(InvoiceStatus status) {
    Invoice invoice = invoiceInState(status);
    List<InvoiceStatusHistory> history = invoice.getHistory();
    List<String> allowed = status == PROCESSING ? List.of("CLOSED", "OPEN") : List.of();

    assertInvalidStatus(
        status, allowed, () -> invoice.replaceItems(List.of(originalItem()), ADMIN, CHANGED_AT));
    assertInvalidStatus(status, allowed, () -> invoice.requestPrint(OPERATOR, CHANGED_AT));
    assertInvalidStatus(
        status, allowed, () -> invoice.cancel("Pedido duplicado", ADMIN, CHANGED_AT));

    assertThat(invoice.getStatus()).isEqualTo(status);
    assertThat(invoice.getHistory()).isEqualTo(history);
  }

  @ParameterizedTest
  @EnumSource(
      value = InvoiceStatus.class,
      names = {"OPEN", "CLOSED", "CANCELED"})
  void allowsConfirmingAndRejectingOnlyWhileProcessing(InvoiceStatus status) {
    Invoice invoice = invoiceInState(status);
    List<InvoiceStatusHistory> history = invoice.getHistory();
    List<String> allowed = status == OPEN ? List.of("OPEN", "PROCESSING", "CANCELED") : List.of();

    assertInvalidStatus(status, allowed, () -> invoice.confirmClose(CHANGED_AT));
    assertInvalidStatus(status, allowed, () -> invoice.rejectClose(FAILURE, CHANGED_AT));

    assertThat(invoice.getStatus()).isEqualTo(status);
    assertThat(invoice.getHistory()).isEqualTo(history);
  }

  @Test
  void keepsNumberVersionAndOrderWhenRestoredFromPersistence() {
    Invoice original = newInvoice();
    var second = InvoiceItem.create(item(UUID.randomUUID(), "B", "Produto B", "0.21", 2), 2);
    Invoice restored =
        new Invoice(
            original.getId(),
            123L,
            OPEN,
            original.getCustomerId(),
            original.getCustomerName(),
            original.getCustomerDocument(),
            List.of(second, original.getItems().getFirst()),
            original.getHistory(),
            null,
            null,
            null,
            null,
            7,
            OPERATOR,
            CREATED_AT,
            null,
            null,
            null,
            null);

    assertThat(restored.getItems()).extracting(InvoiceItem::getLineOrder).containsExactly(1, 2);
    assertThat(restored.getTotal()).isEqualTo(money("1.47"));

    restored.requestPrint(OPERATOR, CHANGED_AT);

    assertThat(restored.getId()).isEqualTo(original.getId());
    assertThat(restored.getNumber()).isEqualTo(123L);
    assertThat(restored.getVersion()).isEqualTo(7L);
    assertThat(restored.getHistory()).hasSize(2);
  }

  private static Customer newCustomer() {
    return Customer.create("Ana Ribeiro", DOCUMENT, null, null, OPERATOR, CREATED_AT);
  }

  private static Invoice newInvoice() {
    return Invoice.create(newCustomer(), List.of(originalItem()), OPERATOR, CREATED_AT);
  }

  private static Invoice invoiceInState(InvoiceStatus status) {
    Invoice invoice = newInvoice();
    switch (status) {
      case OPEN -> {}
      case PROCESSING -> invoice.requestPrint(OPERATOR, CHANGED_AT);
      case CLOSED -> {
        invoice.requestPrint(OPERATOR, CHANGED_AT);
        invoice.confirmClose(CHANGED_AT.plusSeconds(60));
      }
      case CANCELED -> invoice.cancel("Pedido duplicado", ADMIN, CHANGED_AT);
    }
    return invoice;
  }

  private static InvoiceItem.Input originalItem() {
    return item(PRODUCT_ID, "PAR-M6-20", "Parafuso M6", "0.35", 3);
  }

  private static InvoiceItem.Input item(
      UUID productId, String code, String description, String price, int quantity) {
    return new InvoiceItem.Input(
        productId, code, description, money(price), new Quantity(quantity));
  }

  private static Money money(String value) {
    return new Money(new BigDecimal(value));
  }

  private static void assertHistory(Invoice invoice, org.assertj.core.groups.Tuple... entries) {
    assertThat(invoice.getHistory())
        .extracting(InvoiceStatusHistory::getId)
        .doesNotContainNull()
        .doesNotHaveDuplicates();
    assertThat(invoice.getHistory())
        .extracting(
            InvoiceStatusHistory::getFromStatus,
            InvoiceStatusHistory::getToStatus,
            InvoiceStatusHistory::getReason,
            InvoiceStatusHistory::getChangedBy,
            InvoiceStatusHistory::getChangedAt)
        .containsExactly(entries);
  }

  private static void assertInvalidStatus(
      InvoiceStatus status, List<String> allowed, Runnable operation) {
    assertThatThrownBy(operation::run)
        .isInstanceOfSatisfying(
            InvalidInvoiceStatusException.class,
            error -> {
              assertThat(error.getType()).isEqualTo("invalid-invoice-status");
              assertThat(error.getProperties())
                  .containsExactlyInAnyOrderEntriesOf(
                      Map.of("currentStatus", status.name(), "allowedTransitions", allowed));
            });
  }

  private static void assertViolation(String field, Runnable operation) {
    assertThatThrownBy(operation::run)
        .isInstanceOfSatisfying(
            InvalidRequestException.class,
            error -> {
              assertThat(error.getType()).isEqualTo("validation-error");
              assertThat(error.getProperties())
                  .containsEntry(
                      "errors", List.of(Map.of("field", field, "message", error.getDetail())));
            });
  }
}
