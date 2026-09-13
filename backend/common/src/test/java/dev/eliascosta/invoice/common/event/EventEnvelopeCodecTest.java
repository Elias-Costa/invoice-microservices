package dev.eliascosta.invoice.common.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Typed round trip of the envelope through the Boot-configured mapper (requisitos.md §8.7). */
@JsonTest
class EventEnvelopeCodecTest {

  record DebitRequested(UUID printRequestId, UUID invoiceId, int items) {}

  private static final UUID ID = UUID.fromString("e9d1b2c3-4f5a-4b6c-8d7e-9f0a1b2c3d4e");
  private static final UUID INVOICE_ID = UUID.fromString("b6a0c1d2-e3f4-4a5b-9c6d-7e8f9a0b1c2d");
  private static final UUID PRINT_REQUEST_ID =
      UUID.fromString("c7b1d2e3-f4a5-4b6c-8d7e-9f0a1b2c3d4f");
  private static final Instant TIME = Instant.parse("2026-09-12T14:03:22Z");

  @Autowired private JsonMapper mapper;

  private EventEnvelope<DebitRequested> sample() {
    return new EventEnvelope<>(
        ID,
        "stock.debit-requested",
        "billing-service",
        EventEnvelope.SPEC_VERSION,
        TIME,
        INVOICE_ID.toString(),
        new DebitRequested(PRINT_REQUEST_ID, INVOICE_ID, 2));
  }

  @Test
  void roundTripsATypedEnvelope() {
    EventEnvelopeCodec codec = new EventEnvelopeCodec(mapper);

    String json = codec.toJson(sample());
    EventEnvelope<DebitRequested> back = codec.fromJson(json, DebitRequested.class);

    assertThat(back).isEqualTo(sample());
    assertThat(back.data()).isInstanceOf(DebitRequested.class);
  }

  @Test
  void writesTheContractShape() {
    JsonNode json = mapper.readTree(new EventEnvelopeCodec(mapper).toJson(sample()));

    assertThat(json.propertyNames())
        .containsExactly("id", "type", "source", "specversion", "time", "subject", "data");
    assertThat(json.get("time").asString()).isEqualTo("2026-09-12T14:03:22Z");
    assertThat(json.get("data").get("printRequestId").asString())
        .isEqualTo(PRINT_REQUEST_ID.toString());
  }

  @Test
  void rejectsMalformedOrInvalidEnvelopes() {
    EventEnvelopeCodec codec = new EventEnvelopeCodec(mapper);

    assertThatThrownBy(() -> codec.fromJson("{not json", DebitRequested.class))
        .isInstanceOf(JacksonException.class);
    assertThatThrownBy(() -> codec.fromJson("{\"type\":\"x\"}", DebitRequested.class))
        .isInstanceOf(JacksonException.class); // id, source, time, subject and data missing
  }
}
