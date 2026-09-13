package dev.eliascosta.invoice.common.messaging;

import java.time.Instant;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Access to {@code processed_events}. */
public interface ProcessedEventRepository
    extends JpaRepository<ProcessedEventEntity, ProcessedEventId> {

  /**
   * Marks the event as processed by the group unless it already is, in one atomic statement. Must
   * run inside the transaction that applies the event's effect ({@link Propagation#MANDATORY}): a
   * rollback then also removes the mark, so a redelivery is processed again; a concurrent delivery
   * of the same event blocks on the primary key until this transaction ends and then sees the mark.
   *
   * @return 1 when the mark was inserted (first delivery), 0 when it already existed (duplicate)
   */
  @Transactional(propagation = Propagation.MANDATORY)
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      value =
          """
          INSERT INTO processed_events (event_id, consumer_group, event_type, processed_at)
          VALUES (:eventId, :consumerGroup, :eventType, :now)
          ON CONFLICT (event_id, consumer_group) DO NOTHING
          """,
      nativeQuery = true)
  int insertIfAbsent(
      @Param("eventId") UUID eventId,
      @Param("consumerGroup") String consumerGroup,
      @Param("eventType") String eventType,
      @Param("now") Instant now);

  /**
   * Deletes up to {@code batchSize} marks processed at or before {@code cutoff} ({@code
   * modelo-de-dados.md} §12: 30 days — the deduplication window). Served by {@code
   * ix_processed_events_processed_at}.
   *
   * @return rows deleted in this batch
   */
  @Transactional
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      value =
          """
          DELETE FROM processed_events
           WHERE (event_id, consumer_group) IN (
                 SELECT event_id, consumer_group
                   FROM processed_events
                  WHERE processed_at <= :cutoff
                  LIMIT :batchSize)
          """,
      nativeQuery = true)
  int deleteProcessedBefore(@Param("cutoff") Instant cutoff, @Param("batchSize") int batchSize);
}
