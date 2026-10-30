package com.vishop.iam.infra.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vishop.iam.modules.identity.application.api.IdentityApi;
import io.github.coreplatform.common.domain.constants.ApiPaths;
import io.github.coreplatform.common.security.constants.AuthCookies;
import io.github.coreplatform.common.security.gateway.GatewayContextCodec;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.*;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.*;

@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
@EnableMethodSecurity
public class IamSecurityConfiguration {
  @Bean
  PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder(12);
  }

  @Bean
  GatewayContextCodec iamGatewayContext(
      ObjectMapper json, @Value("${vishop.security.context.secret}") String secret) {
    return new GatewayContextCodec(json, secret, "iam-service");
  }

  @Bean
  IamAuthenticationFilter iamAuthenticationFilter(
      GatewayContextCodec codec,
      IdentityApi identity,
      ObjectMapper json,
      @Value("${vishop.security.allowed-origins:http://localhost:3000,http://localhost:8000}")
          String origins) {
    return new IamAuthenticationFilter(codec, identity, json, origins(origins));
  }

  @Bean
  FilterRegistrationBean<IamAuthenticationFilter> filterRegistration(
      IamAuthenticationFilter filter) {
    // Filter chỉ chạy trong Spring Security chain, không đăng ký lần hai ở servlet container.
    var bean = new FilterRegistrationBean<>(filter);
    bean.setEnabled(false);
    return bean;
  }

  @Bean
  CorsConfigurationSource corsConfigurationSource(
      @Value("${vishop.security.allowed-origins:http://localhost:3000,http://localhost:8000}")
          String origins) {
    var cors = new CorsConfiguration();
    cors.setAllowedOrigins(origins(origins).stream().toList());
    cors.setAllowedMethods(
        java.util.List.of("GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS"));
    cors.setAllowedHeaders(
        java.util.List.of(
            "Content-Type",
            "Authorization",
            AuthCookies.REQUEST_HEADER,
            "Idempotency-Key",
            "X-Correlation-Id",
            "X-Request-Id"));
    cors.setAllowCredentials(true);
    var source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", cors);
    return source;
  }

  @Bean
  SecurityFilterChain iamSecurity(HttpSecurity http, IamAuthenticationFilter filter)
      throws Exception {
    // STATELESS tắt HttpSession của Spring. auth_sessions chỉ là trạng thái thu hồi JWT theo sid.
    return http.csrf(AbstractHttpConfigurer::disable)
        .cors(c -> {})
        .formLogin(AbstractHttpConfigurer::disable)
        .httpBasic(AbstractHttpConfigurer::disable)
        .logout(AbstractHttpConfigurer::disable)
        .requestCache(AbstractHttpConfigurer::disable)
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            a ->
                a.requestMatchers(HttpMethod.OPTIONS, "/**")
                    .permitAll()
                    .requestMatchers(
                        ApiPaths.V1 + "/auth/login",
                        ApiPaths.V1 + "/auth/register",
                        ApiPaths.V1 + "/auth/refresh",
                        ApiPaths.V1 + "/auth/logout",
                        "/actuator/health",
                        "/actuator/health/**",
                        "/actuator/info",
                        "/actuator/prometheus",
                        "/v3/api-docs/**",
                        "/error")
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        .exceptionHandling(
            e -> e.authenticationEntryPoint((req, res, failure) -> res.setStatus(401)))
        .addFilterBefore(filter, UsernamePasswordAuthenticationFilter.class)
        .build();
  }

  private Set<String> origins(String value) {
    var allowed =
        Arrays.stream(value.split(","))
            .map(String::trim)
            .filter(s -> !s.isBlank())
            .collect(Collectors.toSet());
    if (allowed.isEmpty() || allowed.contains("*"))
      throw new IllegalArgumentException("Exact allowed origins are required");
    return allowed;
  }
}
