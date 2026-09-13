package dev.eliascosta.invoice.common.idempotency;

/**
 * Outcome of {@link IdempotencyKeyStore#claim}: one case per line of the semantics table in {@code
 * modelo-de-dados.md} §6.3.
 */
sealed interface ClaimResult {

  /** Key was absent (or expired): the row is now {@code IN_FLIGHT} and the handler must run. */
  record Claimed(IdempotencyKeyId id) implements ClaimResult {}

  /** Key exists, {@code COMPLETED}, same route and body: replay without running the handler. */
  record Replay(StoredResponse response) implements ClaimResult {}

  /** Key exists but with a different route or body hash → 422. */
  record Mismatch() implements ClaimResult {}

  /** Key exists and is still {@code IN_FLIGHT} → 409. */
  record InFlight() implements ClaimResult {}
}
