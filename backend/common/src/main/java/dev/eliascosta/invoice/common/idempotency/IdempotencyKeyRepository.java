package dev.eliascosta.invoice.common.idempotency;

import java.time.Instant;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

/**
 * Access to {@code idempotency_keys}. The state transitions that must be atomic under concurrency
 * are native PostgreSQL statements; reads and completion go through the entity. Modifying queries
 * are {@code @Transactional} so they join the caller's transaction or open a read-write one
 * (otherwise they would inherit the read-only default of {@code SimpleJpaRepository}).
 */
public interface IdempotencyKeyRepository
    extends JpaRepository<IdempotencyKeyEntity, IdempotencyKeyId> {

  /**
   * Claims the key for the calling request in one statement ({@code requisitos.md} §8.9, "INSERT
   * IN_FLIGHT"): inserts a new {@code IN_FLIGHT} row, or takes over an existing row whose {@code
   * expires_at} has passed (an expired key is as good as absent, RN-16). A live row — {@code
   * IN_FLIGHT} or {@code COMPLETED} — is left untouched.
   *
   * @return 1 when the key was claimed, 0 when a live row already exists
   */
  @Transactional
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      value =
          """
          INSERT INTO idempotency_keys
              (idempotency_key, user_id, route, request_hash, state,
               response_status, response_body, response_headers, created_at, expires_at)
          VALUES (:key, :userId, :route, :requestHash, 'IN_FLIGHT',
                  NULL, NULL, NULL, :now, :expiresAt)
          ON CONFLICT (idempotency_key, user_id) DO UPDATE
             SET route = EXCLUDED.route,
                 request_hash = EXCLUDED.request_hash,
                 state = 'IN_FLIGHT',
                 response_status = NULL,
                 response_body = NULL,
                 response_headers = NULL,
                 created_at = EXCLUDED.created_at,
                 expires_at = EXCLUDED.expires_at
           WHERE idempotency_keys.expires_at <= EXCLUDED.created_at
          """,
      nativeQuery = true)
  int claim(
      @Param("key") UUID idempotencyKey,
      @Param("userId") String userId,
      @Param("route") String route,
      @Param("requestHash") String requestHash,
      @Param("now") Instant now,
      @Param("expiresAt") Instant expiresAt);

  /**
   * Removes the row claimed by the calling request after a 5xx response or an unhandled exception
   * ({@code modelo-de-dados.md} §6.3: the client may resend with the same key). Guarded by {@code
   * state = 'IN_FLIGHT'} so a completed row is never deleted by mistake.
   *
   * @return 1 when the in-flight row was removed, 0 otherwise
   */
  @Transactional
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      value =
          """
          DELETE FROM idempotency_keys
           WHERE idempotency_key = :key AND user_id = :userId AND state = 'IN_FLIGHT'
          """,
      nativeQuery = true)
  int release(@Param("key") UUID idempotencyKey, @Param("userId") String userId);

  /**
   * Deletes up to {@code batchSize} rows whose {@code expires_at} has passed. PostgreSQL has no
   * {@code DELETE … LIMIT}, so the subselect bounds the batch; it is served by {@code
   * ix_idempotency_keys_expires}.
   *
   * @return rows deleted in this batch
   */
  @Transactional
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      value =
          """
          DELETE FROM idempotency_keys
           WHERE (idempotency_key, user_id) IN (
                 SELECT idempotency_key, user_id
                   FROM idempotency_keys
                  WHERE expires_at <= :now
                  LIMIT :batchSize)
          """,
      nativeQuery = true)
  int deleteExpiredBatch(@Param("now") Instant now, @Param("batchSize") int batchSize);
}
