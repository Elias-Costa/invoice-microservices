package dev.eliascosta.invoice.stock.adapter.in.messaging;

import dev.eliascosta.invoice.common.event.EventEnvelope;
import dev.eliascosta.invoice.common.event.EventEnvelopeCodec;
import dev.eliascosta.invoice.stock.domain.model.Quantity;
import dev.eliascosta.invoice.stock.domain.port.in.DebitStockUseCase;
import dev.eliascosta.invoice.stock.domain.port.in.DebitStockUseCase.Command;
import dev.eliascosta.invoice.stock.domain.port.in.DebitStockUseCase.Item;
import java.util.List;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
class StockDebitRequestListener {

  private static final String TOPIC = "stock.debit-requested";

  private final EventEnvelopeCodec codec;
  private final DebitStockUseCase useCase;

  StockDebitRequestListener(EventEnvelopeCodec codec, DebitStockUseCase useCase) {
    this.codec = codec;
    this.useCase = useCase;
  }

  @KafkaListener(topics = TOPIC, groupId = "${spring.kafka.consumer.group-id}")
  void onRequest(ConsumerRecord<String, String> record) {
    EventEnvelope<DebitRequested> event = codec.fromJson(record.value(), DebitRequested.class);
    DebitRequested data = event.data();
    String invoiceKey = data.invoiceId().toString();
    if (!TOPIC.equals(event.type())
        || !invoiceKey.equals(record.key())
        || !invoiceKey.equals(event.subject())) {
      throw new IllegalArgumentException(
          "Debit request type, Kafka key or subject is inconsistent");
    }
    useCase.debit(
        new Command(
            event.id(),
            data.printRequestId(),
            data.invoiceId(),
            data.invoiceNumber(),
            data.requestedBy(),
            data.items().stream()
                .map(item -> new Item(item.productId(), new Quantity(item.quantity())))
                .toList()));
  }

  private record DebitRequested(
      UUID printRequestId,
      UUID invoiceId,
      long invoiceNumber,
      String requestedBy,
      List<Line> items) {}

  private record Line(UUID productId, int quantity) {}
}
