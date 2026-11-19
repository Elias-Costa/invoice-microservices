package dev.eliascosta.invoice.stock.domain.port.in;

import dev.eliascosta.invoice.stock.domain.model.Quantity;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public interface DebitStockUseCase {

  void debit(Command command);

  record Command(
      UUID eventId,
      UUID printRequestId,
      UUID invoiceId,
      long invoiceNumber,
      String requestedBy,
      List<Item> items) {

    public Command {
      Objects.requireNonNull(eventId, "eventId");
      Objects.requireNonNull(printRequestId, "printRequestId");
      Objects.requireNonNull(invoiceId, "invoiceId");
      Objects.requireNonNull(requestedBy, "requestedBy");
      items = List.copyOf(items);
      if (items.isEmpty()
          || items.stream().map(Item::productId).distinct().count() != items.size()) {
        throw new IllegalArgumentException("Debit requires at least one item and unique products");
      }
    }
  }

  record Item(UUID productId, Quantity quantity) {
    public Item {
      Objects.requireNonNull(productId, "productId");
      if (quantity.value() == 0) {
        throw new IllegalArgumentException("Debit quantity must be positive");
      }
    }
  }
}
