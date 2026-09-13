package dev.eliascosta.invoice.common.messaging;

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
 * Removes processed-event marks older than the retention ({@code modelo-de-dados.md} §12: 30 days,
 * the deduplication window; anything older that is redelivered is stopped by the business keys —
 * {@code invoice_debits}, {@code ux_*}). Schedule from {@code
 * invoice.processed-events.cleanup.cron} (default {@value #DEFAULT_CRON}); each batch is its own
 * transaction. The {@code cleanup_rows_deleted_total} metric arrives with F3-05.
 */
public class ProcessedEventCleaner {

  static final String CRON_PROPERTY = "invoice.processed-events.cleanup.cron";
  static final String DEFAULT_CRON = "0 0 3 * * *";

  private static final Logger log = LoggerFactory.getLogger(ProcessedEventCleaner.class);

  private final ProcessedEventRepository repository;
  private final TransactionTemplate transaction;
  private final Clock clock;
  private final Duration retention;
  private final int batchSize;

  public ProcessedEventCleaner(
      ProcessedEventRepository repository,
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
    int deleted = purgeProcessed();
    log.info("Processed-events cleanup removed {} mark(s) older than {}", deleted, retention);
  }

  /** Deletes every mark older than {@code now - retention}, in batches; returns the total. */
  public int purgeProcessed() {
    Instant cutoff = clock.instant().minus(retention);
    int total = 0;
    int deleted;
    do {
      deleted =
          Objects.requireNonNull(
              transaction.execute(status -> repository.deleteProcessedBefore(cutoff, batchSize)));
      total += deleted;
    } while (deleted == batchSize);
    return total;
  }
}
