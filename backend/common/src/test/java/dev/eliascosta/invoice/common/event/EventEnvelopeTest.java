package dev.eliascosta.invoice.common.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Contract of the event envelope ({@code requisitos.md} §8.7), serialized with the same
 * Boot-configured {@link JsonMapper} the services will use.
 */
@JsonTest
class EventEnvelopeTest {

  record SamplePayload(UUID invoiceId, int quantity) {}

  private static final UUID ID = UUID.fromString("e9d1b2c3-4f5a-4b6c-8d7e-9f0a1b2c3d4e");
  private static final UUID INVOICE_ID = UUID.fromString("b6a0c1d2-e3f4-4a5b-9c6d-7e8f9a0b1c2d");
  private static final Instant TIME = Instant.parse("2026-09-12T14:03:22Z");

  @Autowired private JsonMapper mapper;

  private static EventEnvelope<SamplePayload> sample() {
    return new EventEnvelope<>(
        ID,
        "stock.debit-requested",
        "billing-service",
        EventEnvelope.SPEC_VERSION,
        TIME,
        INVOICE_ID.toString(),
        new SamplePayload(INVOICE_ID, 3));
  }

  @Test
  void serializesMembersInContractOrderWithIsoTime() {
    JsonNode json = mapper.readTree(mapper.writeValueAsString(sample()));

    assertThat(json.propertyNames())
        .containsExactly("id", "type", "source", "specversion", "time", "subject", "data");
    assertThat(json.get("id").asString()).isEqualTo(ID.toString());
    assertThat(json.get("type").asString()).isEqualTo("stock.debit-requested");
    assertThat(json.get("source").asString()).isEqualTo("billing-service");
    assertThat(json.get("specversion").asString()).isEqualTo("1.0");
    assertThat(json.get("time").asString()).isEqualTo("2026-09-12T14:03:22Z");
    assertThat(json.get("subject").asString()).isEqualTo(INVOICE_ID.toString());
    assertThat(json.get("data").get("invoiceId").asString()).isEqualTo(INVOICE_ID.toString());
    assertThat(json.get("data").get("quantity").asInt()).isEqualTo(3);
  }

  @Test
  void roundTripsGenericPayload() {
    EventEnvelope<SamplePayload> original = sample();

    EventEnvelope<SamplePayload> restored =
        mapper.readValue(mapper.writeValueAsString(original), new TypeReference<>() {});

    assertThat(restored).isEqualTo(original);
  }

  @Test
  void deserializesTheWireFormatFromTheSpecification() {
    String wire =
        """
        {
          "id": "e9d1b2c3-4f5a-4b6c-8d7e-9f0a1b2c3d4e",
          "type": "stock.debit-requested",
          "source": "billing-service",
          "specversion": "1.0",
          "time": "2026-09-12T14:03:22Z",
          "subject": "b6a0c1d2-e3f4-4a5b-9c6d-7e8f9a0b1c2d",
          "data": { "invoiceId": "b6a0c1d2-e3f4-4a5b-9c6d-7e8f9a0b1c2d", "quantity": 3 }
        }
        """;

    EventEnvelope<SamplePayload> envelope = mapper.readValue(wire, new TypeReference<>() {});

    assertThat(envelope).isEqualTo(sample());
  }

  @Test
  void factoryFillsIdSpecVersionAndCurrentTime() {
    Instant before = Instant.now();

    EventEnvelope<SamplePayload> envelope =
        EventEnvelope.of(
            "stock.debit-confirmed",
            "stock-service",
            INVOICE_ID.toString(),
            new SamplePayload(INVOICE_ID, 1));

    assertThat(envelope.id()).isNotNull();
    assertThat(envelope.specversion()).isEqualTo("1.0");
    assertThat(envelope.time()).isBetween(before, Instant.now());
    assertThat(envelope.type()).isEqualTo("stock.debit-confirmed");
    assertThat(envelope.source()).isEqualTo("stock-service");
    assertThat(envelope.subject()).isEqualTo(INVOICE_ID.toString());
    assertThat(envelope.data()).isEqualTo(new SamplePayload(INVOICE_ID, 1));
  }

  @Test
  void factoryGeneratesDistinctIds() {
    var payload = new SamplePayload(INVOICE_ID, 1);
    var first = EventEnvelope.of("t", "s", "subject", payload);
    var second = EventEnvelope.of("t", "s", "subject", payload);

    assertThat(first.id()).isNotEqualTo(second.id());
  }

  @Test
  void rejectsNullMembers() {
    var payload = new SamplePayload(INVOICE_ID, 1);
    assertThatNullPointerException()
        .isThrownBy(() -> new EventEnvelope<>(null, "t", "s", "1.0", TIME, "subject", payload))
        .withMessageContaining("id");
    assertThatNullPointerException()
        .isThrownBy(() -> new EventEnvelope<>(ID, "t", "s", "1.0", null, "subject", payload))
        .withMessageContaining("time");
    assertThatNullPointerException()
        .isThrownBy(() -> new EventEnvelope<>(ID, "t", "s", "1.0", TIME, "subject", null))
        .withMessageContaining("data");
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "  "})
  void rejectsBlankStringMembers(String blank) {
    var payload = new SamplePayload(INVOICE_ID, 1);
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new EventEnvelope<>(ID, blank, "s", "1.0", TIME, "subject", payload))
        .withMessageContaining("type");
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new EventEnvelope<>(ID, "t", blank, "1.0", TIME, "subject", payload))
        .withMessageContaining("source");
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new EventEnvelope<>(ID, "t", "s", blank, TIME, "subject", payload))
        .withMessageContaining("specversion");
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new EventEnvelope<>(ID, "t", "s", "1.0", TIME, blank, payload))
        .withMessageContaining("subject");
  }
}
