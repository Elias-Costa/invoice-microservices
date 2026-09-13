package dev.eliascosta.invoice.common.messaging;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.util.Objects;
import java.util.UUID;

/**
 * Primary key of {@code processed_events}: the envelope id scoped by consumer group ({@code
 * modelo-de-dados.md} §6.2 — the same event may legitimately be processed once by each group).
 */
@Embeddable
public record ProcessedEventId(
    @Column(name = "event_id", nullable = false) UUID eventId,
    @Column(name = "consumer_group", nullable = false, length = 100) String consumerGroup) {

  public ProcessedEventId {
    Objects.requireNonNull(eventId, "eventId");
    Objects.requireNonNull(consumerGroup, "consumerGroup");
  }
}
