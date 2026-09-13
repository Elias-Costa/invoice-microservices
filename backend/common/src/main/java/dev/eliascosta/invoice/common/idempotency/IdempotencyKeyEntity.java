package dev.eliascosta.invoice.common.idempotency;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.jspecify.annotations.Nullable;

/**
 * Row of {@code idempotency_keys} ({@code modelo-de-dados.md} §6.3, DDL in Appendix A.3).
 *
 * <p>Rows are <em>claimed</em> by {@link IdempotencyKeyRepository#claim} (native {@code INSERT … ON
 * CONFLICT}, atomic) and later {@link #complete completed} through this entity, so the mapping is
 * validated against the real schema by {@code ddl-auto=validate}. Never updated otherwise.
 */
@Entity
@Table(name = "idempotency_keys")
public class IdempotencyKeyEntity {

  @EmbeddedId private IdempotencyKeyId id;

  @Column(name = "route", nullable = false, length = 200)
  private String route;

  @Column(name = "request_hash", nullable = false, length = 64)
  private String requestHash;

  @Enumerated(EnumType.STRING)
  @Column(name = "state", nullable = false, length = 20)
  private IdempotencyState state;

  @Column(name = "response_status")
  private @Nullable Integer responseStatus;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "response_body")
  private @Nullable String responseBody;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "response_headers")
  private @Nullable String responseHeaders;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;

  /** JPA only. */
  protected IdempotencyKeyEntity() {}

  /**
   * Stores the outcome of the handler (status, JSON body or null, JSON object of headers) and moves
   * the row to {@link IdempotencyState#COMPLETED}. Only valid while {@link
   * IdempotencyState#IN_FLIGHT}.
   */
  void complete(int responseStatus, @Nullable String responseBody, String responseHeaders) {
    if (state != IdempotencyState.IN_FLIGHT) {
      throw new IllegalStateException("Idempotency key " + id + " is not IN_FLIGHT but " + state);
    }
    this.state = IdempotencyState.COMPLETED;
    this.responseStatus = responseStatus;
    this.responseBody = responseBody;
    this.responseHeaders = Objects.requireNonNull(responseHeaders, "responseHeaders");
  }

  public IdempotencyKeyId getId() {
    return id;
  }

  /** {@code <METHOD> <path>} of the original request, e.g. {@code POST /invoices} (D-012). */
  public String getRoute() {
    return route;
  }

  /** SHA-256 (hex) of the canonical request body. */
  public String getRequestHash() {
    return requestHash;
  }

  public IdempotencyState getState() {
    return state;
  }

  public @Nullable Integer getResponseStatus() {
    return responseStatus;
  }

  /**
   * Stored JSON body, or null when the response had no body. As read back from {@code jsonb}: the
   * same JSON document, but PostgreSQL does not preserve whitespace or key order (D-014).
   */
  public @Nullable String getResponseBody() {
    return responseBody;
  }

  /** JSON object with the replayed headers ({@code Content-Type}, {@code Location}). */
  public @Nullable String getResponseHeaders() {
    return responseHeaders;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getExpiresAt() {
    return expiresAt;
  }

  boolean isCompleted() {
    return state == IdempotencyState.COMPLETED;
  }
}
