package dev.eliascosta.invoice.common.idempotency;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Resolves the {@code user_id} that scopes an {@code Idempotency-Key} ({@code requisitos.md} §8.9).
 * A service overrides the default bean to derive the user from its authentication (F2: JWT claim);
 * until then {@link PrincipalNameUserResolver} answers {@value #ANONYMOUS} for unauthenticated
 * requests.
 */
@FunctionalInterface
public interface IdempotencyUserResolver {

  /** {@code user_id} used when the request has no authenticated principal. */
  String ANONYMOUS = "anonymous";

  /** Never null or blank; at most 100 characters ({@code idempotency_keys.user_id}). */
  String resolve(HttpServletRequest request);
}
