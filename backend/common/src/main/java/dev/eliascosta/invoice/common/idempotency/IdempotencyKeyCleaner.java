package dev.eliascosta.invoice.common.idempotency;

import java.time.Clock;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Removes expired keys ({@code modelo-de-dados.md} §12: daily job, batched deletes). Schedule from
 * {@code invoice.idempotency.cleanup.cron} (default {@value #DEFAULT_CRON}); batch size from {@code
 * invoice.idempotency.cleanup.batch-size}. Each batch is its own transaction, so a long backlog
 * never holds one transaction open. The {@code cleanup_rows_deleted_total} metric is added when
 * observability arrives (F3).
 */
class IdempotencyKeyCleaner {

  static final String CRON_PROPERTY = "invoice.idempotency.cleanup.cron";
  static final String DEFAULT_CRON = "0 0 3 * * *";

  private static final Logger log = LoggerFactory.getLogger(IdempotencyKeyCleaner.class);

  private final IdempotencyKeyRepository repository;
  private final TransactionTemplate transaction;
  private final Clock clock;
  private final int batchSize;

  IdempotencyKeyCleaner(
      IdempotencyKeyRepository repository,
      PlatformTransactionManager transactionManager,
      Clock clock,
      int batchSize) {
    if (batchSize < 1) {
      throw new IllegalArgumentException("batchSize must be >= 1, got " + batchSize);
    }
    this.repository = Objects.requireNonNull(repository, "repository");
    this.transaction = new TransactionTemplate(transactionManager);
    this.clock = Objects.requireNonNull(clock, "clock");
    this.batchSize = batchSize;
  }

  @Scheduled(cron = "${" + CRON_PROPERTY + ":" + DEFAULT_CRON + "}")
  void run() {
    int deleted = purgeExpired();
    log.info("Idempotency cleanup removed {} expired key(s)", deleted);
  }

  /** Deletes every expired key in batches; returns the total removed. Safe to call any time. */
  int purgeExpired() {
    int total = 0;
    int deleted;
    do {
      deleted =
          Objects.requireNonNull(
              transaction.execute(
                  status -> repository.deleteExpiredBatch(clock.instant(), batchSize)));
      total += deleted;
    } while (deleted == batchSize);
    return total;
  }
}
