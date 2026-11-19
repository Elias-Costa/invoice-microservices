package dev.eliascosta.invoice.billing.config;

import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
class StockHttpConfiguration {

  static final String INSTANCE = "stockService";

  @Bean
  HttpClient stockHttpTransport(TimeLimiterRegistry timeLimiters) {
    return HttpClient.newBuilder().connectTimeout(timeout(timeLimiters)).build();
  }

  @Bean
  RestClient stockRestClient(
      @Value("${invoice.stock.base-url}") URI baseUrl,
      @Qualifier("stockHttpTransport") HttpClient transport,
      TimeLimiterRegistry timeLimiters) {
    var requestFactory = new JdkClientHttpRequestFactory(transport);
    // The transport deadline also releases the connection when the TimeLimiter completes a stage.
    requestFactory.setReadTimeout(timeout(timeLimiters));
    return RestClient.builder().baseUrl(baseUrl.toString()).requestFactory(requestFactory).build();
  }

  @Bean
  ExecutorService stockHttpExecutor() {
    return Executors.newVirtualThreadPerTaskExecutor();
  }

  private static Duration timeout(TimeLimiterRegistry timeLimiters) {
    return timeLimiters.timeLimiter(INSTANCE).getTimeLimiterConfig().getTimeoutDuration();
  }
}
