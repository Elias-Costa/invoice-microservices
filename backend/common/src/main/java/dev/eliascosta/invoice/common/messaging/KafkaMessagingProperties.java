package dev.eliascosta.invoice.common.messaging;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Settings of the Kafka base configuration, prefix {@code invoice.kafka}.
 *
 * <pre>
 * invoice:
 *   kafka:
 *     topics:
 *       - name: stock.debit-requested
 *         partitions: 3
 *     dead-letter:
 *       partitions: 1
 * </pre>
 *
 * @param topics topics the service declares; each is created with its partitions and a {@code
 *     <name>.DLT} companion (D-017, {@code requisitos.md} §8.7). Creation is idempotent, so every
 *     service may declare the full list
 * @param deadLetter dead-letter topic settings
 * @param producer producer settings beyond the Boot defaults
 * @param consumer consumer error-handling settings
 */
@ConfigurationProperties("invoice.kafka")
public record KafkaMessagingProperties(
    @DefaultValue List<Topic> topics,
    @DefaultValue DeadLetter deadLetter,
    @DefaultValue Producer producer,
    @DefaultValue Consumer consumer) {

  /**
   * @param name topic name
   * @param partitions partition count (§8.7: 3 for the business topics)
   */
  public record Topic(String name, int partitions) {
    public Topic {
      if (name == null || name.isBlank()) {
        throw new IllegalArgumentException("invoice.kafka.topics[].name must not be blank");
      }
      if (partitions < 1) {
        throw new IllegalArgumentException(
            "invoice.kafka.topics[].partitions must be >= 1 for " + name + ", got " + partitions);
      }
    }
  }

  /**
   * @param partitions partition count of every {@code <topic>.DLT} (§8.7: 1)
   */
  public record DeadLetter(@DefaultValue("1") int partitions) {}

  /**
   * @param maxBlock Kafka {@code max.block.ms}: how long {@code send} may block waiting for
   *     metadata (an unreachable broker) before failing; bounded so the outbox poller does not hang
   *     for the client default of 60 s
   */
  public record Producer(@DefaultValue("10s") Duration maxBlock) {}

  /**
   * @param retry retry/backoff of the {@code DefaultErrorHandler} before a record goes to the DLT
   */
  public record Consumer(@DefaultValue Retry retry) {

    /**
     * RNF-RES-02: 5 attempts, exponential backoff starting at 1 s and doubling (1, 2, 4, 8 s).
     *
     * @param maxAttempts total deliveries of a record before it is sent to the DLT (first +
     *     retries)
     * @param initialInterval delay before the first retry
     * @param multiplier growth factor of the delay
     */
    public record Retry(
        @DefaultValue("5") int maxAttempts,
        @DefaultValue("1s") Duration initialInterval,
        @DefaultValue("2.0") double multiplier) {
      public Retry {
        if (maxAttempts < 1) {
          throw new IllegalArgumentException(
              "invoice.kafka.consumer.retry.max-attempts must be >= 1, got " + maxAttempts);
        }
      }
    }
  }
}
