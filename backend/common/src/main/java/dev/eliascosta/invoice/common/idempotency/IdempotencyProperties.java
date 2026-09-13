package dev.eliascosta.invoice.common.idempotency;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Settings of the HTTP idempotency support, prefix {@code invoice.idempotency}.
 *
 * <p>The cleanup schedule is read directly by {@link IdempotencyKeyCleaner} from {@code
 * invoice.idempotency.cleanup.cron} (default {@code 0 0 3 * * *}, {@code modelo-de-dados.md} §12).
 *
 * @param ttl how long a key stays valid after it is claimed (RN-16: 24 h)
 * @param cleanup expired-key cleanup job settings
 */
@ConfigurationProperties("invoice.idempotency")
public record IdempotencyProperties(
    @DefaultValue("24h") Duration ttl, @DefaultValue Cleanup cleanup) {

  /**
   * @param enabled registers the {@code @Scheduled} cleanup job
   * @param batchSize rows deleted per transaction; the job loops until a batch comes back short
   */
  public record Cleanup(
      @DefaultValue("true") boolean enabled, @DefaultValue("1000") int batchSize) {}
}
