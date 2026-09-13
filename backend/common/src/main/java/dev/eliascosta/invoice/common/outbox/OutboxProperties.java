package dev.eliascosta.invoice.common.outbox;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Settings of the transactional outbox, prefix {@code invoice.outbox}.
 *
 * <p>The cleanup schedule is read directly by {@link OutboxCleaner} from {@code
 * invoice.outbox.cleanup.cron} (default {@code 0 0 3 * * *}).
 *
 * @param publisher poller settings
 * @param cleanup published-event cleanup job settings
 */
@ConfigurationProperties("invoice.outbox")
public record OutboxProperties(@DefaultValue Publisher publisher, @DefaultValue Cleanup cleanup) {

  /**
   * @param enabled registers the poller
   * @param pollInterval delay between the end of one batch and the start of the next (RNF-PERF-04:
   *     500 ms)
   * @param batchSize events locked per tick (RNF-PERF-04: 100)
   * @param sendTimeout how long to wait for the broker's acknowledgement of one record (the time
   *     {@code send} itself may block on an unreachable broker is {@code
   *     invoice.kafka.producer.max-block})
   * @param maxAttempts definitive failures after which an event is abandoned (D-015)
   */
  public record Publisher(
      @DefaultValue("true") boolean enabled,
      @DefaultValue("500ms") Duration pollInterval,
      @DefaultValue("100") int batchSize,
      @DefaultValue("10s") Duration sendTimeout,
      @DefaultValue("5") int maxAttempts) {}

  /**
   * @param enabled registers the {@code @Scheduled} cleanup job
   * @param retention how long a published event is kept ({@code modelo-de-dados.md} §12: 7 days)
   * @param batchSize rows deleted per transaction; the job loops until a batch comes back short
   */
  public record Cleanup(
      @DefaultValue("true") boolean enabled,
      @DefaultValue("7d") Duration retention,
      @DefaultValue("1000") int batchSize) {}
}
