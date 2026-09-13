package dev.eliascosta.invoice.common.outbox;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import java.util.Objects;

/**
 * Outbox gauges (RNF-OBS-03, D-015). Micrometer names are dotted; the Prometheus registry renders
 * them as {@code outbox_pending_events} and {@code outbox_failed_events}. Each sample runs a count
 * on the table: pending rows are served by the partial index and both sets stay small thanks to the
 * cleanup job. Bound automatically once the service has a {@link MeterRegistry} (F2-08).
 */
public class OutboxMetrics implements MeterBinder {

  public static final String PENDING_EVENTS = "outbox.pending.events";
  public static final String FAILED_EVENTS = "outbox.failed.events";

  private final OutboxEventRepository repository;

  public OutboxMetrics(OutboxEventRepository repository) {
    this.repository = Objects.requireNonNull(repository, "repository");
  }

  @Override
  public void bindTo(MeterRegistry registry) {
    Gauge.builder(PENDING_EVENTS, repository, OutboxEventRepository::countPending)
        .description("Eventos da outbox aguardando publicação no Kafka")
        .register(registry);
    Gauge.builder(FAILED_EVENTS, repository, OutboxEventRepository::countFailed)
        .description("Eventos da outbox abandonados após falhas definitivas de publicação")
        .register(registry);
  }
}
