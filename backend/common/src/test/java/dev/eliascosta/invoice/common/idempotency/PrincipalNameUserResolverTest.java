package dev.eliascosta.invoice.common.idempotency;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

/** Default {@code user_id} scope before authentication exists (F1): principal name or anonymous. */
class PrincipalNameUserResolverTest {

  private final PrincipalNameUserResolver resolver = new PrincipalNameUserResolver();

  @Test
  void unauthenticatedRequestIsAnonymous() {
    assertThat(resolver.resolve(new MockHttpServletRequest())).isEqualTo("anonymous");
  }

  @Test
  void blankPrincipalNameIsAnonymous() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setUserPrincipal(() -> " ");
    assertThat(resolver.resolve(request)).isEqualTo("anonymous");
  }

  @Test
  void principalNameScopesTheKey() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setUserPrincipal(() -> "operator");
    assertThat(resolver.resolve(request)).isEqualTo("operator");
  }
}
