package dev.eliascosta.invoice.common.idempotency;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * State machine of {@code idempotency_keys} ({@code requisitos.md} §8.9): {@link #claim} before the
 * handler, then {@link #complete} or {@link #release} after it.
 *
 * <p>Every operation runs in its own short transaction, committed before the handler starts (or
 * after it finished): a concurrent request with the same key must see the {@code IN_FLIGHT} row.
 */
class IdempotencyKeyStore {

  private static final Logger log = LoggerFactory.getLogger(IdempotencyKeyStore.class);
  private static final TypeReference<Map<String, String>> HEADERS_TYPE = new TypeReference<>() {};

  private final IdempotencyKeyRepository repository;
  private final TransactionTemplate transaction;
  private final Clock clock;
  private final Duration ttl;
  private final JsonMapper mapper = JsonMapper.builder().build();

  IdempotencyKeyStore(
      IdempotencyKeyRepository repository,
      PlatformTransactionManager transactionManager,
      Clock clock,
      Duration ttl) {
    this.repository = Objects.requireNonNull(repository, "repository");
    this.transaction = new TransactionTemplate(transactionManager);
    this.clock = Objects.requireNonNull(clock, "clock");
    this.ttl = Objects.requireNonNull(ttl, "ttl");
  }

  /**
   * Claims the key or classifies the existing row. The claim is a single atomic statement; when it
   * loses, the winner's row is read and compared. If that row vanished in between (the winner
   * released it after a 5xx), the claim is retried once.
   */
  ClaimResult claim(IdempotencyKeyId id, String route, String requestHash) {
    return Objects.requireNonNull(
        transaction.execute(
            status -> {
              for (int attempt = 0; attempt < 2; attempt++) {
                Instant now = clock.instant();
                int claimed =
                    repository.claim(
                        id.idempotencyKey(), id.userId(), route, requestHash, now, now.plus(ttl));
                if (claimed == 1) {
                  return new ClaimResult.Claimed(id);
                }
                Optional<IdempotencyKeyEntity> existing = repository.findById(id);
                if (existing.isPresent()) {
                  return classify(existing.get(), route, requestHash);
                }
              }
              throw new IllegalStateException("Could not claim or read idempotency key " + id);
            }));
  }

  private ClaimResult classify(IdempotencyKeyEntity existing, String route, String requestHash) {
    if (!existing.getRoute().equals(route) || !existing.getRequestHash().equals(requestHash)) {
      return new ClaimResult.Mismatch();
    }
    if (!existing.isCompleted()) {
      return new ClaimResult.InFlight();
    }
    return new ClaimResult.Replay(
        new StoredResponse(
            Objects.requireNonNull(existing.getResponseStatus(), "responseStatus"),
            existing.getResponseBody(),
            headersFromJson(existing.getResponseHeaders())));
  }

  /** Stores the response for the claimed key. A row that is no longer ours is left alone. */
  void complete(IdempotencyKeyId id, StoredResponse response) {
    transaction.executeWithoutResult(
        status -> {
          Optional<IdempotencyKeyEntity> row = repository.findById(id);
          if (row.isEmpty()) {
            log.warn("Idempotency key {} vanished before completion; response not stored", id);
            return;
          }
          if (row.get().isCompleted()) {
            log.warn("Idempotency key {} was completed elsewhere; response not stored", id);
            return;
          }
          row.get().complete(response.status(), response.body(), headersToJson(response.headers()));
        });
  }

  /** Removes the in-flight row so the client can resend with the same key. */
  void release(IdempotencyKeyId id) {
    int removed =
        Objects.requireNonNull(
            transaction.execute(status -> repository.release(id.idempotencyKey(), id.userId())));
    log.debug("Released idempotency key {} ({} row)", id, removed);
  }

  private String headersToJson(Map<String, String> headers) {
    return mapper.writeValueAsString(headers);
  }

  private Map<String, String> headersFromJson(@Nullable String json) {
    return json == null ? Map.of() : mapper.readValue(json, HEADERS_TYPE);
  }
}
