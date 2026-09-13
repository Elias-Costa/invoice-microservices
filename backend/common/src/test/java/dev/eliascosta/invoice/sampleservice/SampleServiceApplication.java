package dev.eliascosta.invoice.sampleservice;

import dev.eliascosta.invoice.common.idempotency.IdempotencyUserResolver;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;

/**
 * Stand-in for a real service ({@code stock-service}, {@code billing-service}) used by the
 * idempotency integration test: it lives in its own base package and does <em>not</em> scan {@code
 * common}, so the test proves that the auto-configuration alone registers the entity, the
 * repository, the filter and the interceptor.
 *
 * <p>Overrides {@link IdempotencyUserResolver} the way F2 will (JWT claim): here the user comes
 * from the {@value #USER_HEADER} header, defaulting to {@code anonymous}.
 */
@SpringBootConfiguration
@EnableAutoConfiguration
@ComponentScan
public class SampleServiceApplication {

  public static final String USER_HEADER = "X-Test-User";

  @Bean
  IdempotencyUserResolver headerUserResolver() {
    return request -> {
      String user = request.getHeader(USER_HEADER);
      return user == null || user.isBlank() ? IdempotencyUserResolver.ANONYMOUS : user;
    };
  }
}
