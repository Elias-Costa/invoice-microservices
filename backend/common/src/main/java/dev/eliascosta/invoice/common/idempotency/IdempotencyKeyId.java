package dev.eliascosta.invoice.common.idempotency;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.util.Objects;
import java.util.UUID;

/**
 * Primary key of {@code idempotency_keys}: the client-supplied key scoped by user ({@code
 * requisitos.md} §8.9 — the same UUID sent by two users are two independent keys).
 */
@Embeddable
public record IdempotencyKeyId(
    @Column(name = "idempotency_key", nullable = false) UUID idempotencyKey,
    @Column(name = "user_id", nullable = false, length = 100) String userId) {

  public IdempotencyKeyId {
    Objects.requireNonNull(idempotencyKey, "idempotencyKey");
    Objects.requireNonNull(userId, "userId");
  }
}
