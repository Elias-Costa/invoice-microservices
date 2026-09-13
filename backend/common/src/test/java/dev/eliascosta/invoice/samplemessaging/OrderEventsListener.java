package dev.eliascosta.invoice.samplemessaging;

import dev.eliascosta.invoice.common.error.BusinessRuleException;
import dev.eliascosta.invoice.common.event.EventEnvelope;
import dev.eliascosta.invoice.common.event.EventEnvelopeCodec;
import dev.eliascosta.invoice.common.messaging.ProcessedEventGuard;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Listener shaped like the real consumers (F1-11/F1-20): one transaction per record, {@link
 * ProcessedEventGuard} first, then the effect. The {@code customer} field of the payload selects
 * the behaviour so the integration test can drive every path:
 *
 * <ul>
 *   <li>{@value #POISON}: always fails → retries, then DLT;
 *   <li>{@value #REJECTED}: throws {@link BusinessRuleException} → no retry, DLT (D-016);
 *   <li>{@value #FLAKY}: fails on the first delivery only → proves the guard's mark is rolled back
 *       with the effect and the redelivery is processed;
 *   <li>anything else: applied.
 * </ul>
 */
@Component
public class OrderEventsListener {

  public static final String TOPIC = "sample.orders";
  public static final String GROUP = "sample-service";
  public static final String POISON = "poison";
  public static final String REJECTED = "rejected";
  public static final String FLAKY = "flaky";

  public record OrderPlaced(UUID orderId, String customer, int quantity) {}

  private final ProcessedEventGuard guard;
  private final EventEnvelopeCodec codec;
  private final List<ConsumerRecord<String, String>> received = new CopyOnWriteArrayList<>();
  private final List<EventEnvelope<OrderPlaced>> applied = new CopyOnWriteArrayList<>();
  private final Map<UUID, AtomicInteger> deliveries = new ConcurrentHashMap<>();

  OrderEventsListener(ProcessedEventGuard guard, EventEnvelopeCodec codec) {
    this.guard = guard;
    this.codec = codec;
  }

  @KafkaListener(topics = TOPIC, groupId = GROUP)
  @Transactional
  public void on(ConsumerRecord<String, String> record) {
    received.add(record);
    EventEnvelope<OrderPlaced> event = codec.fromJson(record.value(), OrderPlaced.class);
    int delivery =
        deliveries.computeIfAbsent(event.id(), id -> new AtomicInteger()).incrementAndGet();
    if (!guard.tryMark(event, GROUP)) {
      return;
    }
    switch (event.data().customer()) {
      case POISON -> throw new IllegalStateException("poison record " + event.id());
      case REJECTED ->
          throw new BusinessRuleException(
              "insufficient-stock", "Saldo insuficiente", "Rejeitado pelo listener de teste");
      case FLAKY -> {
        if (delivery == 1) {
          throw new IllegalStateException("flaky record " + event.id() + " fails once");
        }
        applied.add(event);
      }
      default -> applied.add(event);
    }
  }

  /** Every record delivered by Kafka, including redeliveries. */
  public List<ConsumerRecord<String, String>> received() {
    return received;
  }

  /** Events whose effect was applied (and committed together with the guard's mark). */
  public List<EventEnvelope<OrderPlaced>> applied() {
    return applied;
  }

  public int deliveries(UUID eventId) {
    return deliveries.getOrDefault(eventId, new AtomicInteger()).get();
  }

  public void reset() {
    received.clear();
    applied.clear();
    deliveries.clear();
  }
}
