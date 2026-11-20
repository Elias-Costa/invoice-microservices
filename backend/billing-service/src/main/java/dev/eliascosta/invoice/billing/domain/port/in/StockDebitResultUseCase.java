package dev.eliascosta.invoice.billing.domain.port.in;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public interface StockDebitResultUseCase {

  void confirm(Result result);

  void reject(Result result, RejectionReason reason, List<RejectedItem> details);

  record Result(UUID eventId, UUID invoiceId, UUID printRequestId) {
    public Result {
      Objects.requireNonNull(eventId, "eventId");
      Objects.requireNonNull(invoiceId, "invoiceId");
      Objects.requireNonNull(printRequestId, "printRequestId");
    }
  }

  enum RejectionReason {
    INSUFFICIENT_STOCK,
    PRODUCT_INACTIVE,
    PRODUCT_NOT_FOUND
  }

  record RejectedItem(UUID productId, String productCode, int requested, Integer available) {}
}
