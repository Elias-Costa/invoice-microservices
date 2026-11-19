package dev.eliascosta.invoice.billing.domain.model;

import static dev.eliascosta.invoice.billing.domain.model.DomainValidation.invalid;
import static dev.eliascosta.invoice.billing.domain.model.DomainValidation.requiredText;

import dev.eliascosta.invoice.billing.domain.exception.DuplicateInvoiceItemException;
import dev.eliascosta.invoice.billing.domain.exception.InvalidInvoiceStatusException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public final class Invoice {

  private static final String SYSTEM_ACTOR = "system";

  private final UUID id;
  // Assigned by the database when the invoice is inserted (RN-06).
  private final Long number;
  private final UUID customerId;
  private final String customerName;
  private final Document customerDocument;
  private final List<InvoiceStatusHistory> history;
  private final long version;
  private final String createdBy;
  private final Instant createdAt;
  private List<InvoiceItem> items;
  private Money total;
  private InvoiceStatus status;
  private UUID printRequestId;
  private Instant processingStartedAt;
  private String lastFailureReason;
  private String cancelReason;
  private String updatedBy;
  private Instant updatedAt;
  private Instant closedAt;
  private Instant canceledAt;

  public Invoice(
      UUID id,
      Long number,
      InvoiceStatus status,
      UUID customerId,
      String customerName,
      Document customerDocument,
      List<InvoiceItem> items,
      List<InvoiceStatusHistory> history,
      UUID printRequestId,
      Instant processingStartedAt,
      String lastFailureReason,
      String cancelReason,
      long version,
      String createdBy,
      Instant createdAt,
      String updatedBy,
      Instant updatedAt,
      Instant closedAt,
      Instant canceledAt) {
    this.id = Objects.requireNonNull(id, "id");
    if (number != null && number < 1) {
      throw new IllegalArgumentException("number must be positive");
    }
    this.number = number;
    this.status = Objects.requireNonNull(status, "status");
    this.customerId = Objects.requireNonNull(customerId, "customerId");
    this.customerName = requiredText(customerName, "customerName", 150);
    this.customerDocument = Objects.requireNonNull(customerDocument, "customerDocument");
    this.items =
        items == null
            ? List.of()
            : items.stream().sorted(Comparator.comparingInt(InvoiceItem::getLineOrder)).toList();
    this.total = totalOf(this.items);
    this.history = new ArrayList<>(List.copyOf(history));
    this.printRequestId = printRequestId;
    this.processingStartedAt = processingStartedAt;
    this.lastFailureReason =
        lastFailureReason == null
            ? null
            : requiredText(lastFailureReason, "lastFailureReason", 500);
    this.cancelReason =
        cancelReason == null ? null : requiredText(cancelReason, "cancelReason", 500);
    this.version = version;
    this.createdBy = requiredText(createdBy, "createdBy", 100);
    this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
    this.updatedBy = updatedBy == null ? null : requiredText(updatedBy, "updatedBy", 100);
    this.updatedAt = updatedAt;
    this.closedAt = closedAt;
    this.canceledAt = canceledAt;
    validateStatusFields();
  }

  public static Invoice create(
      Customer customer, List<InvoiceItem.Input> items, String createdBy, Instant createdAt) {
    customer.ensureActive();
    List<InvoiceItem> createdItems = createItems(items);
    InvoiceStatusHistory creation =
        new InvoiceStatusHistory(
            UUID.randomUUID(), null, InvoiceStatus.OPEN, null, createdBy, createdAt);
    return new Invoice(
        UUID.randomUUID(),
        null,
        InvoiceStatus.OPEN,
        customer.getId(),
        customer.getName(),
        customer.getDocument(),
        createdItems,
        List.of(creation),
        null,
        null,
        null,
        null,
        0,
        createdBy,
        createdAt,
        null,
        null,
        null,
        null);
  }

  public void replaceItems(List<InvoiceItem.Input> items, String changedBy, Instant changedAt) {
    ensureOpen();
    List<InvoiceItem> replacement = createItems(items);
    Money replacementTotal = totalOf(replacement);
    Objects.requireNonNull(changedAt, "changedAt");
    updatedBy = requiredText(changedBy, "changedBy", 100);
    updatedAt = changedAt;
    this.items = replacement;
    total = replacementTotal;
  }

  public UUID requestPrint(String changedBy, Instant changedAt) {
    ensureOpen();
    UUID requestId = UUID.randomUUID();
    transitionTo(InvoiceStatus.PROCESSING, null, changedBy, changedAt);
    printRequestId = requestId;
    processingStartedAt = changedAt;
    return requestId;
  }

  public void confirmClose(Instant changedAt) {
    requireStatus(InvoiceStatus.PROCESSING);
    transitionTo(InvoiceStatus.CLOSED, null, SYSTEM_ACTOR, changedAt);
    closedAt = changedAt;
    processingStartedAt = null;
    lastFailureReason = null;
  }

  public void rejectClose(String reason, Instant changedAt) {
    requireStatus(InvoiceStatus.PROCESSING);
    String validatedReason = requiredText(reason, "reason", 500);
    transitionTo(InvoiceStatus.OPEN, validatedReason, SYSTEM_ACTOR, changedAt);
    lastFailureReason = validatedReason;
    processingStartedAt = null;
  }

  public void cancel(String reason, String changedBy, Instant changedAt) {
    ensureOpen();
    String validatedReason = requiredText(reason, "reason", 500);
    transitionTo(InvoiceStatus.CANCELED, validatedReason, changedBy, changedAt);
    canceledAt = changedAt;
    cancelReason = validatedReason;
  }

  public void ensureOpen() {
    requireStatus(InvoiceStatus.OPEN);
  }

  private void transitionTo(InvoiceStatus target, String reason, String actor, Instant at) {
    InvoiceStatusHistory entry =
        new InvoiceStatusHistory(UUID.randomUUID(), status, target, reason, actor, at);
    history.add(entry);
    status = target;
    updatedBy = entry.getChangedBy();
    updatedAt = entry.getChangedAt();
  }

  private void requireStatus(InvoiceStatus expected) {
    if (status != expected) {
      throw new InvalidInvoiceStatusException(status);
    }
  }

  private void validateStatusFields() {
    if ((status == InvoiceStatus.CLOSED) != (closedAt != null)
        || (status == InvoiceStatus.CANCELED) != (canceledAt != null)
        || (status == InvoiceStatus.CANCELED) != (cancelReason != null)
        || (status == InvoiceStatus.PROCESSING
            && (printRequestId == null || processingStartedAt == null))) {
      throw new IllegalArgumentException("Invoice fields are inconsistent with status " + status);
    }
  }

  private static List<InvoiceItem> createItems(List<InvoiceItem.Input> inputs) {
    if (inputs == null || inputs.isEmpty()) {
      throw invalid("items", "A nota deve conter pelo menos um item.");
    }
    List<InvoiceItem> items = new ArrayList<>(inputs.size());
    for (InvoiceItem.Input input : inputs) {
      if (input == null) {
        throw invalid("items", "A lista contém um item inválido.");
      }
      items.add(InvoiceItem.create(input, items.size() + 1));
    }
    return List.copyOf(items);
  }

  private static Money totalOf(List<InvoiceItem> items) {
    if (items.isEmpty()) {
      throw invalid("items", "A nota deve conter pelo menos um item.");
    }
    Set<UUID> products = new HashSet<>();
    BigDecimal total = BigDecimal.ZERO;
    for (InvoiceItem item : items) {
      if (!products.add(item.getProductId())) {
        throw new DuplicateInvoiceItemException(item.getProductId());
      }
      total = total.add(item.getLineTotal().value());
    }
    return Money.calculatedTotal(total, "total");
  }

  public UUID getId() {
    return id;
  }

  public Long getNumber() {
    return number;
  }

  public InvoiceStatus getStatus() {
    return status;
  }

  public UUID getCustomerId() {
    return customerId;
  }

  public String getCustomerName() {
    return customerName;
  }

  public Document getCustomerDocument() {
    return customerDocument;
  }

  public List<InvoiceItem> getItems() {
    return items;
  }

  public Money getTotal() {
    return total;
  }

  public List<InvoiceStatusHistory> getHistory() {
    return List.copyOf(history);
  }

  public UUID getPrintRequestId() {
    return printRequestId;
  }

  public Instant getProcessingStartedAt() {
    return processingStartedAt;
  }

  public String getLastFailureReason() {
    return lastFailureReason;
  }

  public String getCancelReason() {
    return cancelReason;
  }

  public long getVersion() {
    return version;
  }

  public String getCreatedBy() {
    return createdBy;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public String getUpdatedBy() {
    return updatedBy;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public Instant getClosedAt() {
    return closedAt;
  }

  public Instant getCanceledAt() {
    return canceledAt;
  }
}
