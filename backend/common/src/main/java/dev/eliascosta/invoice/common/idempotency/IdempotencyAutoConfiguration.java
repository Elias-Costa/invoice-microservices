package dev.eliascosta.invoice.common.idempotency;

import jakarta.persistence.EntityManager;
import java.time.Clock;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurationPackage;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication.Type;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration;
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Wires HTTP idempotency into every servlet service that depends on {@code common} (RN-16, {@code
 * requisitos.md} §8.9).
 *
 * <p>{@code @AutoConfigurationPackage} adds this package to the packages Boot scans for entities
 * and Spring Data repositories, alongside the service's own base package — the services do not
 * component-scan {@code common}. It must run before the JPA auto-configurations read that list,
 * hence the {@code before} ordering. A service that declares its own {@code @EntityScan} or
 * {@code @EnableJpaRepositories} must include this package explicitly.
 *
 * <p>Overridable beans: {@link IdempotencyUserResolver} (F2 swaps in the JWT claim) and {@link
 * Clock}.
 */
@AutoConfiguration(
    before = {HibernateJpaAutoConfiguration.class, DataJpaRepositoriesAutoConfiguration.class})
@ConditionalOnWebApplication(type = Type.SERVLET)
@ConditionalOnClass({EntityManager.class, JpaRepository.class})
@AutoConfigurationPackage(basePackageClasses = IdempotencyKeyEntity.class)
@EnableConfigurationProperties(IdempotencyProperties.class)
public class IdempotencyAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean
  IdempotencyUserResolver idempotencyUserResolver() {
    return new PrincipalNameUserResolver();
  }

  @Bean
  @ConditionalOnMissingBean
  Clock clock() {
    return Clock.systemUTC();
  }

  @Bean
  IdempotencyKeyStore idempotencyKeyStore(
      IdempotencyKeyRepository repository,
      PlatformTransactionManager transactionManager,
      Clock clock,
      IdempotencyProperties properties) {
    return new IdempotencyKeyStore(repository, transactionManager, clock, properties.ttl());
  }

  @Bean
  IdempotencyInterceptor idempotencyInterceptor(
      IdempotencyKeyStore store, IdempotencyUserResolver userResolver) {
    return new IdempotencyInterceptor(store, userResolver, new CanonicalJsonHasher());
  }

  @Bean
  WebMvcConfigurer idempotencyWebMvcConfigurer(IdempotencyInterceptor interceptor) {
    return new WebMvcConfigurer() {
      @Override
      public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(interceptor);
      }
    };
  }

  @Bean
  FilterRegistrationBean<IdempotencyFilter> idempotencyFilter() {
    FilterRegistrationBean<IdempotencyFilter> registration =
        new FilterRegistrationBean<>(new IdempotencyFilter());
    registration.setName("idempotencyFilter");
    // Default (lowest) order: runs inside the security filter chain (F2) and encoding filters.
    return registration;
  }

  /**
   * Expired-key cleanup ({@code modelo-de-dados.md} §12); off with {@code cleanup.enabled=false}.
   */
  @Configuration(proxyBeanMethods = false)
  @EnableScheduling
  @ConditionalOnProperty(
      name = "invoice.idempotency.cleanup.enabled",
      havingValue = "true",
      matchIfMissing = true)
  static class CleanupConfiguration {

    @Bean
    IdempotencyKeyCleaner idempotencyKeyCleaner(
        IdempotencyKeyRepository repository,
        PlatformTransactionManager transactionManager,
        Clock clock,
        IdempotencyProperties properties) {
      return new IdempotencyKeyCleaner(
          repository, transactionManager, clock, properties.cleanup().batchSize());
    }
  }
}
