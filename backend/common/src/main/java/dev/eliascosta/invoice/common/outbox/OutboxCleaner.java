package dev.eliascosta.invoice.common.outbox;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Removes events published longer ago than the retention ({@code modelo-de-dados.md} §12: 7 days,
 * daily job). Abandoned rows ({@code failed_at}) are kept as evidence (D-015). Schedule from {@code
 * invoice.outbox.cleanup.cron} (default {@value #DEFAULT_CRON}); each batch is its own transaction.
 * The {@code cleanup_rows_deleted_total} metric arrives with F3-05.
 */
public class OutboxCleaner {

  static final String CRON_PROPERTY = "invoice.outbox.cleanup.cron";
  static final String DEFAULT_CRON = "0 0 3 * * *";

  private static final Logger log = LoggerFactory.getLogger(OutboxCleaner.class);

  private final OutboxEventRepository repository;
  private final TransactionTemplate transaction;
  private final Clock clock;
  private final Duration retention;
  private final int batchSize;

  public OutboxCleaner(
      OutboxEventRepository repository,
      PlatformTransactionManager transactionManager,
      Clock clock,
      Duration retention,
      int batchSize) {
    if (batchSize < 1) {
      throw new IllegalArgumentException("batchSize must be >= 1, got " + batchSize);
    }
    if (retention.isNegative()) {
      throw new IllegalArgumentException("retention must not be negative, got " + retention);
    }
    this.repository = Objects.requireNonNull(repository, "repository");
    this.transaction = new TransactionTemplate(transactionManager);
    this.clock = Objects.requireNonNull(clock, "clock");
    this.retention = retention;
    this.batchSize = batchSize;
  }

  @Scheduled(cron = "${" + CRON_PROPERTY + ":" + DEFAULT_CRON + "}")
  void run() {
    int deleted = purgePublished();
    log.info("Outbox cleanup removed {} published event(s) older than {}", deleted, retention);
  }

  /**
   * Deletes every event published before {@code now - retention}, in batches; returns the total.
   */
  public int purgePublished() {
    Instant cutoff = clock.instant().minus(retention);
    int total = 0;
    int deleted;
    do {
      deleted =
          Objects.requireNonNull(
              transaction.execute(status -> repository.deletePublishedBefore(cutoff, batchSize)));
      total += deleted;
    } while (deleted == batchSize);
    return total;
  }
}
