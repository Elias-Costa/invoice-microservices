package dev.eliascosta.invoice.common.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/** Defaults of the messaging settings match RNF-RES-02, §8.7 and modelo-de-dados.md §12. */
class KafkaMessagingPropertiesTest {

  @EnableConfigurationProperties({KafkaMessagingProperties.class, ProcessedEventProperties.class})
  static class Config {}

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner().withUserConfiguration(Config.class);

  @Test
  void defaultsFollowTheRequirements() {
    runner.run(
        context -> {
          KafkaMessagingProperties kafka = context.getBean(KafkaMessagingProperties.class);
          assertThat(kafka.topics()).isEmpty();
          assertThat(kafka.deadLetter().partitions()).isEqualTo(1);
          assertThat(kafka.producer().maxBlock()).isEqualTo(Duration.ofSeconds(10));
          assertThat(kafka.consumer().retry())
              .isEqualTo(
                  new KafkaMessagingProperties.Consumer.Retry(5, Duration.ofSeconds(1), 2.0));

          ProcessedEventProperties processed = context.getBean(ProcessedEventProperties.class);
          assertThat(processed.cleanup())
              .isEqualTo(new ProcessedEventProperties.Cleanup(true, Duration.ofDays(30), 1000));
        });
  }

  @Test
  void bindsTheTopicList() {
    runner
        .withPropertyValues(
            "invoice.kafka.topics[0].name=stock.debit-requested",
            "invoice.kafka.topics[0].partitions=3",
            "invoice.kafka.topics[1].name=stock.debit-confirmed",
            "invoice.kafka.topics[1].partitions=3",
            "invoice.kafka.dead-letter.partitions=2",
            "invoice.kafka.consumer.retry.max-attempts=3",
            "invoice.kafka.consumer.retry.initial-interval=250ms")
        .run(
            context -> {
              KafkaMessagingProperties kafka = context.getBean(KafkaMessagingProperties.class);
              assertThat(kafka.topics())
                  .containsExactly(
                      new KafkaMessagingProperties.Topic("stock.debit-requested", 3),
                      new KafkaMessagingProperties.Topic("stock.debit-confirmed", 3));
              assertThat(kafka.deadLetter().partitions()).isEqualTo(2);
              assertThat(kafka.consumer().retry().maxAttempts()).isEqualTo(3);
              assertThat(kafka.consumer().retry().initialInterval())
                  .isEqualTo(Duration.ofMillis(250));
            });
  }

  @Test
  void rejectsATopicWithoutPartitions() {
    runner
        .withPropertyValues("invoice.kafka.topics[0].name=stock.debit-requested")
        .run(
            context -> {
              assertThat(context).hasFailed();
              assertThat(context.getStartupFailure())
                  .hasStackTraceContaining("partitions must be >= 1");
            });
  }

  @Test
  void rejectsZeroRetryAttempts() {
    runner
        .withPropertyValues("invoice.kafka.consumer.retry.max-attempts=0")
        .run(
            context -> {
              assertThat(context).hasFailed();
              assertThat(context.getStartupFailure())
                  .hasStackTraceContaining("max-attempts must be >= 1");
            });
  }
}
