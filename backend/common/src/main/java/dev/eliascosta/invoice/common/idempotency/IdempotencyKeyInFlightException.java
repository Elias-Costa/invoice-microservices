package dev.eliascosta.invoice.common.idempotency;

import dev.eliascosta.invoice.common.error.ConflictException;
import dev.eliascosta.invoice.common.error.ProblemTypes;
import java.util.Map;
import java.util.UUID;

/** 409 {@code idempotency-key-in-flight}: the same key is still being processed (RN-16). */
public class IdempotencyKeyInFlightException extends ConflictException {

  public IdempotencyKeyInFlightException(UUID idempotencyKey) {
    super(
        ProblemTypes.IDEMPOTENCY_KEY_IN_FLIGHT,
        "Requisição em processamento",
        "Já existe uma requisição em processamento com a Idempotency-Key "
            + idempotencyKey
            + ". Aguarde a conclusão antes de reenviar.",
        Map.of("idempotencyKey", idempotencyKey.toString()));
  }
}
