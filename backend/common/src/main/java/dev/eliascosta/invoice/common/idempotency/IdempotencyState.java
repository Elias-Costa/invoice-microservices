package dev.eliascosta.invoice.common.idempotency;

/** Values of {@code idempotency_keys.state} ({@code ck_idempotency_keys_state}). */
public enum IdempotencyState {
  /** Claimed; the handler is running (or the service died before completing it, D-013). */
  IN_FLIGHT,
  /** Response stored; further requests with the same key and payload replay it. */
  COMPLETED
}
