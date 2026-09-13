package dev.eliascosta.invoice.common.idempotency;

import jakarta.servlet.http.HttpServletRequest;
import java.security.Principal;

/**
 * Default {@link IdempotencyUserResolver}: the name of the servlet {@link Principal} when there is
 * one, otherwise {@value IdempotencyUserResolver#ANONYMOUS} (F1 runs without authentication).
 */
class PrincipalNameUserResolver implements IdempotencyUserResolver {

  @Override
  public String resolve(HttpServletRequest request) {
    Principal principal = request.getUserPrincipal();
    if (principal == null || principal.getName() == null || principal.getName().isBlank()) {
      return ANONYMOUS;
    }
    return principal.getName();
  }
}
