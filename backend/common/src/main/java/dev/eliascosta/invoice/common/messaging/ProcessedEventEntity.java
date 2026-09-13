package dev.eliascosta.invoice.common.messaging;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Row of {@code processed_events} ({@code modelo-de-dados.md} §6.2, DDL in Appendix A.3): proof
 * that a consumer group already applied the effect of an event (RNF-RES-03, RN-17).
 *
 * <p>Append-only. Rows are written by {@link ProcessedEventRepository#insertIfAbsent} (native
 * {@code INSERT … ON CONFLICT DO NOTHING}, so the check and the mark are one atomic statement); the
 * entity exists for reads and for {@code ddl-auto=validate}.
 */
@Entity
@Table(name = "processed_events")
public class ProcessedEventEntity {

  @EmbeddedId private ProcessedEventId id;

  @Column(name = "event_type", nullable = false, length = 100)
  private String eventType;

  @Column(name = "processed_at", nullable = false)
  private Instant processedAt;

  /** JPA only. */
  protected ProcessedEventEntity() {}

  public ProcessedEventId getId() {
    return id;
  }

  /** Envelope {@code type}, kept for diagnosis. */
  public String getEventType() {
    return eventType;
  }

  public Instant getProcessedAt() {
    return processedAt;
  }
}
