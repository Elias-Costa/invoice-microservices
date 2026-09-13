package dev.eliascosta.invoice.common.error.web;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication.Type;
import org.springframework.context.annotation.Bean;

/**
 * Registers the shared {@link ProblemDetailExceptionHandler} in every servlet service that depends
 * on {@code common}. Auto-configuration (not component scan) because {@code common} lives outside
 * the services' base packages. Each bean is overridable ({@code @ConditionalOnMissingBean}).
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = Type.SERVLET)
public class ErrorHandlingAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean
  TraceIdResolver traceIdResolver() {
    return new TraceIdResolver();
  }

  @Bean
  @ConditionalOnMissingBean
  ProblemDetailExceptionHandler problemDetailExceptionHandler(TraceIdResolver traceIdResolver) {
    return new ProblemDetailExceptionHandler(traceIdResolver);
  }
}
