package com.vishop.gateway.config;

import java.util.Arrays;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

@Configuration
public class CorsConfig {

  // #region corsWebFilter
  @Bean
  public CorsWebFilter corsWebFilter(
      @org.springframework.beans.factory.annotation.Value(
              "${vishop.security.allowed-origins:http://localhost:3000}")
          String origins) {
    CorsConfiguration config = new CorsConfiguration();
    config.setAllowCredentials(true);
    config.setAllowedOrigins(
        Arrays.stream(origins.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList());
    config.setAllowedHeaders(
        Arrays.asList(
            "Origin",
            "Content-Type",
            "Accept",
            "Authorization",
            "X-Tenant-Id",
            "X-Correlation-Id",
            "X-Request-Id",
            "Idempotency-Key"));
    config.setAllowedMethods(
        Arrays.asList("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS", "HEAD"));
    config.setExposedHeaders(Arrays.asList("X-Correlation-Id", "X-Request-Id", "Location"));
    config.setMaxAge(3600L);

    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", config);

    return new CorsWebFilter(source);
  }
  // #endregion
}
