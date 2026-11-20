package dev.eliascosta.invoice.billing.adapter.in.messaging;

import dev.eliascosta.invoice.billing.domain.port.in.StockDebitResultUseCase;
import dev.eliascosta.invoice.billing.domain.port.in.StockDebitResultUseCase.RejectedItem;
import dev.eliascosta.invoice.billing.domain.port.in.StockDebitResultUseCase.RejectionReason;
import dev.eliascosta.invoice.billing.domain.port.in.StockDebitResultUseCase.Result;
import dev.eliascosta.invoice.common.event.EventEnvelope;
import dev.eliascosta.invoice.common.event.EventEnvelopeCodec;
import java.util.List;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
class StockDebitResultListener {

  private static final String CONFIRMED = "stock.debit-confirmed";
  private static final String REJECTED = "stock.debit-rejected";

  private final EventEnvelopeCodec codec;
  private final StockDebitResultUseCase useCase;

  StockDebitResultListener(EventEnvelopeCodec codec, StockDebitResultUseCase useCase) {
    this.codec = codec;
    this.useCase = useCase;
  }

  @KafkaListener(topics = CONFIRMED, groupId = "${spring.kafka.consumer.group-id}")
  void onConfirmation(ConsumerRecord<String, String> record) {
    var event = codec.fromJson(record.value(), DebitConfirmed.class);
    DebitConfirmed data = event.data();
    useCase.confirm(result(record, event, data.invoiceId(), data.printRequestId()));
  }

  @KafkaListener(topics = REJECTED, groupId = "${spring.kafka.consumer.group-id}")
  void onRejection(ConsumerRecord<String, String> record) {
    var event = codec.fromJson(record.value(), DebitRejected.class);
    DebitRejected data = event.data();
    useCase.reject(
        result(record, event, data.invoiceId(), data.printRequestId()),
        data.reason(),
        data.details());
  }

  private static Result result(
      ConsumerRecord<String, String> record,
      EventEnvelope<?> event,
      UUID invoiceId,
      UUID printRequestId) {
    String key = invoiceId.toString();
    if (!record.topic().equals(event.type())
        || !key.equals(record.key())
        || !key.equals(event.subject())) {
      throw new IllegalArgumentException("Debit result type, Kafka key or subject is inconsistent");
    }
    return new Result(event.id(), invoiceId, printRequestId);
  }

  private record DebitConfirmed(UUID printRequestId, UUID invoiceId, List<ConfirmedItem> items) {}

  private record ConfirmedItem(UUID productId, int quantity, int balanceAfter) {}

  private record DebitRejected(
      UUID printRequestId, UUID invoiceId, RejectionReason reason, List<RejectedItem> details) {}
}
