package dev.eliascosta.invoice.common.error;

import java.net.URI;

/**
 * Slugs of the error catalogue ({@code requisitos.md} §8.8) that the {@code common} module itself
 * produces, plus the URN prefix shared by every {@code ProblemDetail.type}.
 *
 * <p>Service-specific slugs ({@code product-not-found}, {@code invalid-invoice-status}, ...) are
 * declared by the exceptions that raise them, next to their domain.
 */
public final class ProblemTypes {

  public static final String URN_PREFIX = "urn:problem-type:";

  /** 400 — Bean Validation or malformed input. */
  public static final String VALIDATION_ERROR = "validation-error";

  /** 409 — concurrent edit detected by {@code @Version}. */
  public static final String OPTIMISTIC_LOCK_CONFLICT = "optimistic-lock-conflict";

  /** 409 — unique constraint violated at the database (safety net, D-011). */
  public static final String UNIQUE_CONSTRAINT_VIOLATION = "unique-constraint-violation";

  /** 409 — a request with the same {@code Idempotency-Key} is still being processed (RN-16). */
  public static final String IDEMPOTENCY_KEY_IN_FLIGHT = "idempotency-key-in-flight";

  /** 422 — {@code Idempotency-Key} reused with a different body or route (RN-16). */
  public static final String IDEMPOTENCY_KEY_PAYLOAD_MISMATCH = "idempotency-key-payload-mismatch";

  /** 422 — other database constraint violated (FK, CHECK, NOT NULL) (safety net, D-011). */
  public static final String DATA_INTEGRITY_VIOLATION = "data-integrity-violation";

  /** 500 — unmapped exception; generic detail, stack trace only in the log. */
  public static final String INTERNAL_ERROR = "internal-error";

  private ProblemTypes() {}

  /** Builds the {@code type} URI for a catalogue slug: {@code urn:problem-type:<slug>}. */
  public static URI uri(String slug) {
    return URI.create(URN_PREFIX + slug);
  }
}
