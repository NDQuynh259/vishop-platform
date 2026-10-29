package com.vishop.subscription.infra.security;

import io.github.coreplatform.common.domain.constants.ApiPaths;
import io.github.coreplatform.common.security.jwt.JwtTokenProvider;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration(proxyBeanMethods = false)
@EnableMethodSecurity
public class SubscriptionSecurityConfiguration {
  @Bean
  SecurityFilterChain billingSecurity(HttpSecurity http, JwtTokenProvider tokens) throws Exception {
    return http.csrf(AbstractHttpConfigurer::disable)
        .formLogin(AbstractHttpConfigurer::disable)
        .httpBasic(AbstractHttpConfigurer::disable)
        .logout(AbstractHttpConfigurer::disable)
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            a ->
                a.dispatcherTypeMatchers(jakarta.servlet.DispatcherType.ERROR)
                    .permitAll()
                    .requestMatchers(
                        "/actuator/health",
                        "/actuator/health/**",
                        "/actuator/info",
                        "/actuator/prometheus",
                        "/v3/api-docs/**")
                    .permitAll()
                    .requestMatchers(HttpMethod.POST, ApiPaths.V1 + "/billing/webhooks/sepay")
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        .exceptionHandling(e -> e.authenticationEntryPoint((req, res, ex) -> res.sendError(401)))
        .addFilterBefore(
            new SubscriptionAccessTokenFilter(tokens), UsernamePasswordAuthenticationFilter.class)
        .build();
  }
}
