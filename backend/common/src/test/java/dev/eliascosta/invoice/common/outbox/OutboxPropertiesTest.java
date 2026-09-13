package dev.eliascosta.invoice.common.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/** Defaults of the outbox settings match RNF-PERF-04, D-015 and modelo-de-dados.md §12. */
class OutboxPropertiesTest {

  @EnableConfigurationProperties(OutboxProperties.class)
  static class Config {}

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner().withUserConfiguration(Config.class);

  @Test
  void defaultsFollowTheRequirements() {
    runner.run(
        context -> {
          OutboxProperties outbox = context.getBean(OutboxProperties.class);
          assertThat(outbox.publisher())
              .isEqualTo(
                  new OutboxProperties.Publisher(
                      true, Duration.ofMillis(500), 100, Duration.ofSeconds(10), 5));
          assertThat(outbox.cleanup())
              .isEqualTo(new OutboxProperties.Cleanup(true, Duration.ofDays(7), 1000));
        });
  }

  @Test
  void bindsOverrides() {
    runner
        .withPropertyValues(
            "invoice.outbox.publisher.enabled=false",
            "invoice.outbox.publisher.poll-interval=1s",
            "invoice.outbox.publisher.batch-size=10",
            "invoice.outbox.publisher.send-timeout=2s",
            "invoice.outbox.publisher.max-attempts=3",
            "invoice.outbox.cleanup.retention=1d",
            "invoice.outbox.cleanup.batch-size=50")
        .run(
            context -> {
              OutboxProperties outbox = context.getBean(OutboxProperties.class);
              assertThat(outbox.publisher())
                  .isEqualTo(
                      new OutboxProperties.Publisher(
                          false, Duration.ofSeconds(1), 10, Duration.ofSeconds(2), 3));
              assertThat(outbox.cleanup())
                  .isEqualTo(new OutboxProperties.Cleanup(true, Duration.ofDays(1), 50));
            });
  }
}
