package dev.eliascosta.invoice.stock.domain.model;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class InvoiceDebit {

  private final UUID printRequestId;
  private final UUID invoiceId;
  private final long invoiceNumber;
  private final UUID requestEventId;
  private final Result result;
  private final Instant processedAt;

  public InvoiceDebit(
      UUID printRequestId,
      UUID invoiceId,
      long invoiceNumber,
      UUID requestEventId,
      Result result,
      Instant processedAt) {
    this.printRequestId = Objects.requireNonNull(printRequestId, "printRequestId");
    this.invoiceId = Objects.requireNonNull(invoiceId, "invoiceId");
    this.invoiceNumber = invoiceNumber;
    this.requestEventId = Objects.requireNonNull(requestEventId, "requestEventId");
    this.result = Objects.requireNonNull(result, "result");
    this.processedAt = Objects.requireNonNull(processedAt, "processedAt");
  }

  public UUID getPrintRequestId() {
    return printRequestId;
  }

  public UUID getInvoiceId() {
    return invoiceId;
  }

  public long getInvoiceNumber() {
    return invoiceNumber;
  }

  public UUID getRequestEventId() {
    return requestEventId;
  }

  public Result getResult() {
    return result;
  }

  public Instant getProcessedAt() {
    return processedAt;
  }

  public enum Outcome {
    CONFIRMED,
    REJECTED
  }

  public enum RejectionReason {
    PRODUCT_NOT_FOUND,
    PRODUCT_INACTIVE,
    INSUFFICIENT_STOCK
  }

  public sealed interface Result permits Confirmed, Rejected {}

  public record Confirmed(List<ConfirmedItem> items) implements Result {
    public Confirmed {
      items = List.copyOf(items);
    }
  }

  public record Rejected(RejectionReason reason, List<RejectedItem> details) implements Result {
    public Rejected {
      Objects.requireNonNull(reason, "reason");
      details = List.copyOf(details);
    }
  }

  public record ConfirmedItem(UUID productId, int quantity, int balanceAfter) {}

  public record RejectedItem(
      UUID productId, String productCode, int requested, Integer available) {}
}
