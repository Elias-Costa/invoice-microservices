package dev.eliascosta.invoice.common.outbox;

import dev.eliascosta.invoice.common.event.EventEnvelope;
import dev.eliascosta.invoice.common.event.EventEnvelopeCodec;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.json.JsonMapper;

/**
 * Records an event in the outbox <em>inside the caller's transaction</em> (RNF-ARC-05): the event
 * is committed — and later published — if and only if the state change is. Use cases call it
 * instead of {@code KafkaTemplate.send}.
 *
 * <p>The row id is the envelope id ({@code modelo-de-dados.md} §6.1), the payload is the envelope
 * serialized once and for all, and the record headers are {@value #EVENT_TYPE_HEADER} and {@value
 * #EVENT_ID_HEADER} ({@code requisitos.md} §8.7) plus whatever the caller adds ({@code traceparent}
 * in F2-08).
 */
public class OutboxWriter {

  public static final String EVENT_TYPE_HEADER = "event-type";
  public static final String EVENT_ID_HEADER = "event-id";

  private static final Logger log = LoggerFactory.getLogger(OutboxWriter.class);

  private final OutboxEventRepository repository;
  private final EventEnvelopeCodec codec;
  private final JsonMapper mapper;
  private final Clock clock;

  public OutboxWriter(
      OutboxEventRepository repository, EventEnvelopeCodec codec, JsonMapper mapper, Clock clock) {
    this.repository = Objects.requireNonNull(repository, "repository");
    this.codec = Objects.requireNonNull(codec, "codec");
    this.mapper = Objects.requireNonNull(mapper, "mapper");
    this.clock = Objects.requireNonNull(clock, "clock");
  }

  /**
   * @param topic destination topic
   * @param partitionKey Kafka message key ({@code invoiceId} — ordering per invoice)
   * @param aggregateType type of the aggregate that changed ({@code Invoice}, {@code Product})
   * @param aggregateId id of that aggregate
   * @param envelope the event; its id becomes the row id
   * @throws IllegalStateException when called outside a transaction
   */
  public void write(
      String topic,
      String partitionKey,
      String aggregateType,
      UUID aggregateId,
      EventEnvelope<?> envelope) {
    write(topic, partitionKey, aggregateType, aggregateId, envelope, Map.of());
  }

  /**
   * Same as {@link #write(String, String, String, UUID, EventEnvelope)} with extra record headers
   * (the contract headers always win on a name clash).
   */
  public void write(
      String topic,
      String partitionKey,
      String aggregateType,
      UUID aggregateId,
      EventEnvelope<?> envelope,
      Map<String, String> extraHeaders) {
    Objects.requireNonNull(envelope, "envelope");
    if (!TransactionSynchronizationManager.isActualTransactionActive()) {
      throw new IllegalStateException(
          "OutboxWriter.write must run inside the transaction that changes the aggregate state"
              + " (RNF-ARC-05); no transaction is active");
    }
    Map<String, String> headers = new LinkedHashMap<>(extraHeaders);
    headers.put(EVENT_TYPE_HEADER, envelope.type());
    headers.put(EVENT_ID_HEADER, envelope.id().toString());

    OutboxEventEntity row =
        new OutboxEventEntity(
            envelope.id(),
            aggregateType,
            aggregateId,
            envelope.type(),
            topic,
            partitionKey,
            codec.toJson(envelope),
            mapper.writeValueAsString(headers),
            clock.instant());
    repository.save(row);
    log.debug("Outbox event {} ({}) queued for topic {}", envelope.id(), envelope.type(), topic);
  }
}
