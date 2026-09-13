package dev.eliascosta.invoice.common.messaging;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Settings of the processed-events table, prefix {@code invoice.processed-events}.
 *
 * <p>The cleanup schedule is read directly by {@link ProcessedEventCleaner} from {@code
 * invoice.processed-events.cleanup.cron} (default {@code 0 0 3 * * *}).
 *
 * @param cleanup cleanup job settings
 */
@ConfigurationProperties("invoice.processed-events")
public record ProcessedEventProperties(@DefaultValue Cleanup cleanup) {

  /**
   * @param enabled registers the {@code @Scheduled} cleanup job
   * @param retention how long a mark is kept ({@code modelo-de-dados.md} §12: 30 days)
   * @param batchSize rows deleted per transaction; the job loops until a batch comes back short
   */
  public record Cleanup(
      @DefaultValue("true") boolean enabled,
      @DefaultValue("30d") Duration retention,
      @DefaultValue("1000") int batchSize) {}
}
