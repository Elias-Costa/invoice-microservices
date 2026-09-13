package dev.eliascosta.invoice.common.outbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Persistable;

/**
 * Row of {@code outbox_events} ({@code modelo-de-dados.md} §6.1, DDL in Appendix A.3): one event
 * waiting to be published, written in the same transaction as the state change it announces
 * (RNF-ARC-05).
 *
 * <p>Life cycle: pending ({@code published_at} and {@code failed_at} both null) → {@link
 * #markPublished published}, or → abandoned ({@link #abandon}) after {@code max-attempts}
 * definitive failures (D-015). {@code ck_outbox_events_terminal} forbids both. The id is the
 * envelope id, assigned by the writer, so the entity implements {@link Persistable} to be {@code
 * persist}ed (not merged) by {@code save}.
 */
@Entity
@Table(name = "outbox_events")
public class OutboxEventEntity implements Persistable<UUID> {

  @Id
  @Column(name = "id", nullable = false)
  private UUID id;

  @Column(name = "aggregate_type", nullable = false, length = 50)
  private String aggregateType;

  @Column(name = "aggregate_id", nullable = false)
  private UUID aggregateId;

  @Column(name = "event_type", nullable = false, length = 100)
  private String eventType;

  @Column(name = "topic", nullable = false, length = 100)
  private String topic;

  @Column(name = "partition_key", nullable = false, length = 100)
  private String partitionKey;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "payload", nullable = false)
  private String payload;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "headers", nullable = false)
  private String headers;

  @Column(name = "attempts", nullable = false)
  private int attempts;

  @Column(name = "last_error", columnDefinition = "text")
  private @Nullable String lastError;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "published_at")
  private @Nullable Instant publishedAt;

  @Column(name = "failed_at")
  private @Nullable Instant failedAt;

  @Transient private boolean persisted;

  /** JPA only. */
  protected OutboxEventEntity() {}

  OutboxEventEntity(
      UUID id,
      String aggregateType,
      UUID aggregateId,
      String eventType,
      String topic,
      String partitionKey,
      String payload,
      String headers,
      Instant createdAt) {
    this.id = Objects.requireNonNull(id, "id");
    this.aggregateType = Objects.requireNonNull(aggregateType, "aggregateType");
    this.aggregateId = Objects.requireNonNull(aggregateId, "aggregateId");
    this.eventType = Objects.requireNonNull(eventType, "eventType");
    this.topic = Objects.requireNonNull(topic, "topic");
    this.partitionKey = Objects.requireNonNull(partitionKey, "partitionKey");
    this.payload = Objects.requireNonNull(payload, "payload");
    this.headers = Objects.requireNonNull(headers, "headers");
    this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
  }

  /** The record was acknowledged by the broker; the successful attempt counts too. */
  void markPublished(Instant at) {
    requirePending();
    this.attempts++;
    this.publishedAt = Objects.requireNonNull(at, "at");
  }

  /**
   * One more publication attempt failed: counts it and keeps the cause for diagnosis.
   *
   * @return attempts so far, including this one
   */
  int recordFailure(String error) {
    requirePending();
    this.attempts++;
    this.lastError = Objects.requireNonNull(error, "error");
    return attempts;
  }

  /** Gives up on this event (D-015): it leaves the poller's view until reset by hand. */
  void abandon(Instant at) {
    requirePending();
    this.failedAt = Objects.requireNonNull(at, "at");
  }

  private void requirePending() {
    if (publishedAt != null || failedAt != null) {
      throw new IllegalStateException("Outbox event " + id + " is no longer pending");
    }
  }

  @PostPersist
  @PostLoad
  void markPersisted() {
    this.persisted = true;
  }

  @Override
  public boolean isNew() {
    return !persisted;
  }

  /** Envelope id; also the consumer-side deduplication key. */
  @Override
  public UUID getId() {
    return id;
  }

  public String getAggregateType() {
    return aggregateType;
  }

  public UUID getAggregateId() {
    return aggregateId;
  }

  public String getEventType() {
    return eventType;
  }

  public String getTopic() {
    return topic;
  }

  /** Kafka message key ({@code invoiceId} in this system — ordering per invoice, §8.7). */
  public String getPartitionKey() {
    return partitionKey;
  }

  /** The full envelope as JSON; sent verbatim as the record value. */
  public String getPayload() {
    return payload;
  }

  /**
   * JSON object of record headers ({@code event-type}, {@code event-id}, later {@code
   * traceparent}).
   */
  public String getHeaders() {
    return headers;
  }

  /** Publication attempts so far, the successful one included (1 = published first time). */
  public int getAttempts() {
    return attempts;
  }

  public @Nullable String getLastError() {
    return lastError;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public @Nullable Instant getPublishedAt() {
    return publishedAt;
  }

  public @Nullable Instant getFailedAt() {
    return failedAt;
  }

  public boolean isPending() {
    return publishedAt == null && failedAt == null;
  }
}
