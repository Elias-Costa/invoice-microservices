package dev.eliascosta.invoice.common.event;

import java.util.Objects;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.json.JsonMapper;

/**
 * JSON (de)serialization of {@link EventEnvelope} with the Boot-configured {@link JsonMapper}: the
 * outbox stores exactly what {@link #toJson} produces, the poller sends it verbatim as the record
 * value, and listeners turn the received value back into a typed envelope with {@link #fromJson}.
 *
 * <p>The mapper is shared with the REST layer, so an event round-trips with the same conventions as
 * an HTTP body ({@code time} as ISO-8601 UTC, unknown properties tolerated by the Boot defaults).
 */
public class EventEnvelopeCodec {

  private final JsonMapper mapper;

  public EventEnvelopeCodec(JsonMapper mapper) {
    this.mapper = Objects.requireNonNull(mapper, "mapper");
  }

  public String toJson(EventEnvelope<?> envelope) {
    return mapper.writeValueAsString(Objects.requireNonNull(envelope, "envelope"));
  }

  /**
   * Parses an envelope whose {@code data} member is a {@code dataType}.
   *
   * @throws tools.jackson.databind.DatabindException when the JSON is not a valid envelope (a
   *     listener that lets it propagate sends the record to the DLT after the configured retries)
   */
  public <T> EventEnvelope<T> fromJson(String json, Class<T> dataType) {
    JavaType type =
        mapper
            .getTypeFactory()
            .constructParametricType(
                EventEnvelope.class, Objects.requireNonNull(dataType, "dataType"));
    return mapper.readValue(Objects.requireNonNull(json, "json"), type);
  }
}
