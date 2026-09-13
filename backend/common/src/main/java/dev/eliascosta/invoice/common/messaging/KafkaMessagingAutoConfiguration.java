package dev.eliascosta.invoice.common.messaging;

import dev.eliascosta.invoice.common.error.BusinessRuleException;
import dev.eliascosta.invoice.common.event.EventEnvelopeCodec;
import dev.eliascosta.invoice.common.idempotency.IdempotencyAutoConfiguration;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.util.Map;
import java.util.stream.Stream;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.TopicPartition;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurationPackage;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration;
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration;
import org.springframework.boot.kafka.autoconfigure.DefaultKafkaProducerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.PlatformTransactionManager;
import tools.jackson.databind.json.JsonMapper;

/**
 * Kafka base configuration shared by the services ({@code requisitos.md} §8.7, §8.11, RNF-RES-02,
 * RNF-RES-03), layered on Boot's own {@code KafkaAutoConfiguration}:
 *
 * <ul>
 *   <li>producer: {@code acks=all}, {@code enable.idempotence=true}; serializers stay Boot's
 *       defaults ({@code String}/{@code String}) — the record value is the envelope JSON as stored
 *       in the outbox;
 *   <li>consumer errors: {@link DefaultErrorHandler} with exponential backoff (5 attempts, 1 s × 2)
 *       and a {@link DeadLetterPublishingRecoverer} to {@code <topic>.DLT}; {@link
 *       BusinessRuleException} is not retried and goes straight to the DLT (D-016) — consumers are
 *       expected to turn business rules into {@code rejected} events themselves;
 *   <li>topics: every entry of {@code invoice.kafka.topics} and its {@code .DLT} companion are
 *       created by the Boot {@link KafkaAdmin} (D-017);
 *   <li>{@link ProcessedEventGuard} and {@link EventEnvelopeCodec} for listeners; the {@code
 *       processed_events} entity joins the JPA scan through {@code @AutoConfigurationPackage} (same
 *       caveat as {@link IdempotencyAutoConfiguration}).
 * </ul>
 *
 * <p>Left to each service's {@code application.yml}: {@code spring.kafka.bootstrap-servers}, {@code
 * spring.kafka.consumer.group-id}, {@code spring.kafka.consumer.auto-offset-reset=earliest} and the
 * topic list.
 */
@AutoConfiguration(
    before = {HibernateJpaAutoConfiguration.class, DataJpaRepositoriesAutoConfiguration.class},
    after = IdempotencyAutoConfiguration.class)
@ConditionalOnClass({EntityManager.class, JpaRepository.class, KafkaTemplate.class})
@AutoConfigurationPackage(basePackageClasses = ProcessedEventEntity.class)
@EnableConfigurationProperties({KafkaMessagingProperties.class, ProcessedEventProperties.class})
public class KafkaMessagingAutoConfiguration {

  /** Suffix of dead-letter topics ({@code requisitos.md} §8.7). */
  public static final String DEAD_LETTER_SUFFIX = ".DLT";

  @Bean
  @ConditionalOnMissingBean
  Clock clock() {
    return Clock.systemUTC();
  }

  @Bean
  @ConditionalOnMissingBean
  EventEnvelopeCodec eventEnvelopeCodec(JsonMapper mapper) {
    return new EventEnvelopeCodec(mapper);
  }

  @Bean
  ProcessedEventGuard processedEventGuard(ProcessedEventRepository repository, Clock clock) {
    return new ProcessedEventGuard(repository, clock);
  }

  /** Producer guarantees of §8.11; {@code max.block.ms} bounded so a poll never hangs. */
  @Bean
  DefaultKafkaProducerFactoryCustomizer invoiceProducerFactoryCustomizer(
      KafkaMessagingProperties properties) {
    return factory ->
        factory.updateConfigs(
            Map.of(
                ProducerConfig.ACKS_CONFIG,
                "all",
                ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG,
                Boolean.TRUE,
                ProducerConfig.MAX_BLOCK_MS_CONFIG,
                properties.producer().maxBlock().toMillis()));
  }

  /**
   * Picked up by Boot's listener container factory. The DLT record goes to {@code <topic>.DLT} with
   * the partition left to Kafka (the DLT has fewer partitions than the source topic).
   */
  @Bean
  @ConditionalOnMissingBean(CommonErrorHandler.class)
  DefaultErrorHandler kafkaErrorHandler(
      KafkaTemplate<Object, Object> kafkaTemplate, KafkaMessagingProperties properties) {
    DeadLetterPublishingRecoverer recoverer =
        new DeadLetterPublishingRecoverer(
            kafkaTemplate,
            (record, exception) -> new TopicPartition(record.topic() + DEAD_LETTER_SUFFIX, -1));
    KafkaMessagingProperties.Consumer.Retry retry = properties.consumer().retry();
    ExponentialBackOffWithMaxRetries backOff =
        new ExponentialBackOffWithMaxRetries(retry.maxAttempts() - 1);
    backOff.setInitialInterval(retry.initialInterval().toMillis());
    backOff.setMultiplier(retry.multiplier());
    DefaultErrorHandler handler = new DefaultErrorHandler(recoverer, backOff);
    handler.addNotRetryableExceptions(BusinessRuleException.class);
    return handler;
  }

  /** Declared topics plus their dead-letter companions; empty when the service declares none. */
  @Bean
  KafkaAdmin.NewTopics invoiceTopics(KafkaMessagingProperties properties) {
    int dltPartitions = properties.deadLetter().partitions();
    NewTopic[] topics =
        properties.topics().stream()
            .flatMap(
                topic ->
                    Stream.of(
                        TopicBuilder.name(topic.name()).partitions(topic.partitions()).build(),
                        TopicBuilder.name(topic.name() + DEAD_LETTER_SUFFIX)
                            .partitions(dltPartitions)
                            .build()))
            .toArray(NewTopic[]::new);
    return new KafkaAdmin.NewTopics(topics);
  }

  /**
   * Processed-events cleanup ({@code modelo-de-dados.md} §12); off with {@code
   * cleanup.enabled=false}.
   */
  @Configuration(proxyBeanMethods = false)
  @EnableScheduling
  @ConditionalOnProperty(
      name = "invoice.processed-events.cleanup.enabled",
      havingValue = "true",
      matchIfMissing = true)
  static class CleanupConfiguration {

    @Bean
    ProcessedEventCleaner processedEventCleaner(
        ProcessedEventRepository repository,
        PlatformTransactionManager transactionManager,
        Clock clock,
        ProcessedEventProperties properties) {
      ProcessedEventProperties.Cleanup cleanup = properties.cleanup();
      return new ProcessedEventCleaner(
          repository, transactionManager, clock, cleanup.retention(), cleanup.batchSize());
    }
  }
}
