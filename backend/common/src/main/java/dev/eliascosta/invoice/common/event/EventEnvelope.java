package dev.eliascosta.invoice.common.event;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Common envelope of every Kafka event ({@code requisitos.md} §8.7), inspired by CloudEvents.
 *
 * <p>Serialized with the Boot-configured Jackson mapper: records need no annotations and {@code
 * time} is written as an ISO-8601 UTC string. Consumers deserialize with a {@code
 * TypeReference<EventEnvelope<Payload>>}.
 *
 * @param id event id (UUID) — deduplication key on the consumer side ({@code processed_events})
 * @param type event type, e.g. {@code stock.debit-requested}
 * @param source producing service, e.g. {@code billing-service}
 * @param specversion envelope version, always {@value #SPEC_VERSION} for now
 * @param time instant the event was produced (UTC)
 * @param subject id of the aggregate the event is about (the {@code invoiceId} in this system)
 * @param data event payload
 * @param <T> payload type
 */
public record EventEnvelope<T>(
    UUID id, String type, String source, String specversion, Instant time, String subject, T data) {

  public static final String SPEC_VERSION = "1.0";

  public EventEnvelope {
    Objects.requireNonNull(id, "id must not be null");
    requireNonBlank(type, "type");
    requireNonBlank(source, "source");
    requireNonBlank(specversion, "specversion");
    Objects.requireNonNull(time, "time must not be null");
    requireNonBlank(subject, "subject");
    Objects.requireNonNull(data, "data must not be null");
  }

  /** Creates a new envelope with a random id, the current spec version and the current time. */
  public static <T> EventEnvelope<T> of(String type, String source, String subject, T data) {
    return new EventEnvelope<>(
        UUID.randomUUID(), type, source, SPEC_VERSION, Instant.now(), subject, data);
  }

  private static void requireNonBlank(String value, String name) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(name + " must not be blank");
    }
  }
}
