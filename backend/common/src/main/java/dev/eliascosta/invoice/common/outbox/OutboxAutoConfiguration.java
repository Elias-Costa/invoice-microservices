package dev.eliascosta.invoice.common.outbox;

import dev.eliascosta.invoice.common.event.EventEnvelopeCodec;
import dev.eliascosta.invoice.common.idempotency.IdempotencyAutoConfiguration;
import dev.eliascosta.invoice.common.messaging.KafkaMessagingAutoConfiguration;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.Duration;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurationPackage;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration;
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.FixedDelayTask;
import org.springframework.transaction.PlatformTransactionManager;
import tools.jackson.databind.json.JsonMapper;

/**
 * Wires the transactional outbox (RNF-ARC-05, {@code requisitos.md} §8.11) into every service that
 * depends on {@code common}: entity and repository join the JPA scan through
 * {@code @AutoConfigurationPackage} (same mechanism and caveat as {@link
 * IdempotencyAutoConfiguration}), {@link OutboxWriter} is available to use cases, and — unless
 * {@code invoice.outbox.publisher.enabled=false} — the {@link OutboxPublisher} runs on the shared
 * task scheduler with the configured fixed delay.
 *
 * <p>Ordered after the messaging auto-configuration for the {@link EventEnvelopeCodec} and the
 * producer settings, and after idempotency so the single {@link Clock} bean is shared.
 */
@AutoConfiguration(
    before = {HibernateJpaAutoConfiguration.class, DataJpaRepositoriesAutoConfiguration.class},
    after = {IdempotencyAutoConfiguration.class, KafkaMessagingAutoConfiguration.class})
@ConditionalOnClass({EntityManager.class, JpaRepository.class, KafkaTemplate.class})
@AutoConfigurationPackage(basePackageClasses = OutboxEventEntity.class)
@EnableConfigurationProperties(OutboxProperties.class)
public class OutboxAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean
  Clock clock() {
    return Clock.systemUTC();
  }

  @Bean
  OutboxWriter outboxWriter(
      OutboxEventRepository repository, EventEnvelopeCodec codec, JsonMapper mapper, Clock clock) {
    return new OutboxWriter(repository, codec, mapper, clock);
  }

  /** Bound to the {@code MeterRegistry} by Boot's metrics support when the service has one. */
  @Bean
  OutboxMetrics outboxMetrics(OutboxEventRepository repository) {
    return new OutboxMetrics(repository);
  }

  @Configuration(proxyBeanMethods = false)
  @EnableScheduling
  @ConditionalOnProperty(
      name = "invoice.outbox.publisher.enabled",
      havingValue = "true",
      matchIfMissing = true)
  static class PublisherConfiguration {

    @Bean
    OutboxPublisher outboxPublisher(
        OutboxEventRepository repository,
        KafkaTemplate<String, String> kafkaTemplate,
        PlatformTransactionManager transactionManager,
        JsonMapper mapper,
        Clock clock,
        OutboxProperties properties) {
      OutboxProperties.Publisher settings = properties.publisher();
      return new OutboxPublisher(
          repository,
          kafkaTemplate,
          transactionManager,
          mapper,
          clock,
          settings.batchSize(),
          settings.sendTimeout(),
          settings.maxAttempts());
    }

    /**
     * Registers the poll as a fixed-delay task: {@code @Scheduled(fixedDelayString)} only accepts
     * ISO-8601 or plain milliseconds, while the property is a Boot {@link Duration} ({@code
     * 500ms}).
     */
    @Bean
    SchedulingConfigurer outboxPublisherSchedule(
        OutboxPublisher publisher, OutboxProperties properties) {
      Duration interval = properties.publisher().pollInterval();
      return registrar ->
          registrar.addFixedDelayTask(
              new FixedDelayTask(publisher::publishPending, interval, interval));
    }
  }

  /**
   * Published-event cleanup ({@code modelo-de-dados.md} §12); off with {@code
   * cleanup.enabled=false}.
   */
  @Configuration(proxyBeanMethods = false)
  @EnableScheduling
  @ConditionalOnProperty(
      name = "invoice.outbox.cleanup.enabled",
      havingValue = "true",
      matchIfMissing = true)
  static class CleanupConfiguration {

    @Bean
    OutboxCleaner outboxCleaner(
        OutboxEventRepository repository,
        PlatformTransactionManager transactionManager,
        Clock clock,
        OutboxProperties properties) {
      OutboxProperties.Cleanup cleanup = properties.cleanup();
      return new OutboxCleaner(
          repository, transactionManager, clock, cleanup.retention(), cleanup.batchSize());
    }
  }
}
