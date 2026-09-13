package dev.eliascosta.invoice.common.outbox;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Access to {@code outbox_events}. The poller's batch selection is native SQL because JPQL has no
 * {@code SKIP LOCKED}; cleanup is native for the bounded delete. Modifying queries are
 * {@code @Transactional} so they join the caller's transaction or open a read-write one.
 */
public interface OutboxEventRepository extends JpaRepository<OutboxEventEntity, UUID> {

  /**
   * Locks and returns the oldest pending events, skipping rows another poller instance already
   * holds ({@code requisitos.md} §8.11, {@code modelo-de-dados.md} §11.3). Served by the partial
   * index {@code ix_outbox_events_pending}. The lock lives as long as the caller's transaction, so
   * the caller <em>must</em> already be in one ({@link Propagation#MANDATORY}): outside a
   * transaction the lock would be released before the rows were even returned.
   */
  @Transactional(propagation = Propagation.MANDATORY)
  @Query(
      value =
          """
          SELECT * FROM outbox_events
           WHERE published_at IS NULL AND failed_at IS NULL
           ORDER BY created_at, id
           LIMIT :limit
           FOR UPDATE SKIP LOCKED
          """,
      nativeQuery = true)
  List<OutboxEventEntity> lockPendingBatch(@Param("limit") int limit);

  /** Events still waiting for publication (gauge {@code outbox_pending_events}, RNF-OBS-03). */
  @Query(
      "SELECT count(e) FROM OutboxEventEntity e WHERE e.publishedAt IS NULL AND e.failedAt IS NULL")
  long countPending();

  /** Events abandoned after definitive failures (gauge {@code outbox_failed_events}, D-015). */
  @Query("SELECT count(e) FROM OutboxEventEntity e WHERE e.failedAt IS NOT NULL")
  long countFailed();

  /**
   * Deletes up to {@code batchSize} events published at or before {@code cutoff} ({@code
   * modelo-de-dados.md} §12: published for more than 7 days). Pending and abandoned rows are never
   * touched. PostgreSQL has no {@code DELETE … LIMIT}, so the subselect bounds the batch.
   *
   * @return rows deleted in this batch
   */
  @Transactional
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      value =
          """
          DELETE FROM outbox_events
           WHERE id IN (
                 SELECT id
                   FROM outbox_events
                  WHERE published_at IS NOT NULL AND published_at <= :cutoff
                  LIMIT :batchSize)
          """,
      nativeQuery = true)
  int deletePublishedBefore(@Param("cutoff") Instant cutoff, @Param("batchSize") int batchSize);
}
