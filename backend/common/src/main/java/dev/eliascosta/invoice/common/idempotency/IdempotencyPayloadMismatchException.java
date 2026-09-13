package dev.eliascosta.invoice.common.idempotency;

import dev.eliascosta.invoice.common.error.BusinessRuleException;
import dev.eliascosta.invoice.common.error.ProblemTypes;
import java.util.Map;
import java.util.UUID;

/**
 * 422 {@code idempotency-key-payload-mismatch}: the key was already used with a different body or
 * route (RN-16, D-012).
 */
public class IdempotencyPayloadMismatchException extends BusinessRuleException {

  public IdempotencyPayloadMismatchException(UUID idempotencyKey) {
    super(
        ProblemTypes.IDEMPOTENCY_KEY_PAYLOAD_MISMATCH,
        "Idempotency-Key reutilizada com dados diferentes",
        "A Idempotency-Key "
            + idempotencyKey
            + " já foi usada com outro corpo ou rota. Gere uma nova chave para uma nova operação.",
        Map.of("idempotencyKey", idempotencyKey.toString()));
  }
}
