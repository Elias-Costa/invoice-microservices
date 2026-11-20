package dev.eliascosta.invoice.billing.adapter.out.messaging;

import dev.eliascosta.invoice.billing.domain.model.Invoice;
import dev.eliascosta.invoice.billing.domain.port.out.StockDebitRequestPublisher;
import dev.eliascosta.invoice.common.event.EventEnvelope;
import dev.eliascosta.invoice.common.outbox.OutboxWriter;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
class StockDebitRequestOutboxPublisher implements StockDebitRequestPublisher {

  private static final String TOPIC = "stock.debit-requested";
  private final OutboxWriter outbox;

  StockDebitRequestOutboxPublisher(OutboxWriter outbox) {
    this.outbox = outbox;
  }

  @Override
  public void enqueue(Invoice invoice) {
    UUID invoiceId = invoice.getId();
    String key = invoiceId.toString();
    var data =
        new DebitRequested(
            invoice.getPrintRequestId(),
            invoiceId,
            invoice.getNumber(),
            invoice.getUpdatedBy(),
            invoice.getItems().stream()
                .map(item -> new Item(item.getProductId(), item.getQuantity().value()))
                .toList());
    var event = EventEnvelope.of(TOPIC, "billing-service", key, data);
    outbox.write(TOPIC, key, "Invoice", invoiceId, event);
  }

  private record DebitRequested(
      UUID printRequestId,
      UUID invoiceId,
      long invoiceNumber,
      String requestedBy,
      List<Item> items) {}

  private record Item(UUID productId, int quantity) {}
}
