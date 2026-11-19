package dev.eliascosta.invoice.stock.domain.port.in;

import dev.eliascosta.invoice.stock.domain.model.Quantity;
import java.util.List;
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
      items = List.copyOf(items);
    }
  }

  record Item(UUID productId, Quantity quantity) {}
}
