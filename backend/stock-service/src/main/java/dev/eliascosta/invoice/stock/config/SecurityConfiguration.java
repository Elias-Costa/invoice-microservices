package dev.eliascosta.invoice.stock.config;

import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;

/** The MVP accepts anonymous API requests until OAuth2 Resource Server is configured. */
@Configuration(proxyBeanMethods = false)
@ImportAutoConfiguration(exclude = UserDetailsServiceAutoConfiguration.class)
class SecurityConfiguration {

  @Bean
  SecurityFilterChain stockSecurityFilterChain(HttpSecurity http) throws Exception {
    return http.authorizeHttpRequests(requests -> requests.anyRequest().permitAll())
        .csrf(AbstractHttpConfigurer::disable)
        .build();
  }
}
