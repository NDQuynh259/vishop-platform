package com.vishop.audit.infra.security;

import io.github.coreplatform.common.domain.constants.ApiPaths;
import io.github.coreplatform.common.domain.constants.SystemRole;
import io.github.coreplatform.common.security.gateway.GatewayContextCodec;
import io.github.coreplatform.common.security.gateway.GatewayPrincipal;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration(proxyBeanMethods = false)
public class AuditSecurityConfiguration {
  @Bean
  public SecurityFilterChain auditSecurityFilterChain(HttpSecurity http, GatewayContextCodec tokens)
      throws Exception {
    return http.csrf(AbstractHttpConfigurer::disable)
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .exceptionHandling(
            errors -> errors.authenticationEntryPoint((req, res, ex) -> res.sendError(401)))
        .authorizeHttpRequests(
            auth ->
                auth.dispatcherTypeMatchers(jakarta.servlet.DispatcherType.ERROR)
                    .permitAll()
                    .requestMatchers(
                        "/actuator/health",
                        "/actuator/health/**",
                        "/actuator/info",
                        "/actuator/prometheus",
                        "/v3/api-docs/**")
                    .permitAll()
                    .requestMatchers(
                        ApiPaths.V1 + "/cms/audit-logs", ApiPaths.V1 + "/cms/audit-logs/**")
                    .access(
                        (authentication, context) ->
                            new AuthorizationDecision(
                                authentication.get().getPrincipal()
                                        instanceof GatewayPrincipal actor
                                    && SystemRole.PLATFORM_ADMIN.name().equals(actor.systemRole())))
                    .anyRequest()
                    .denyAll())
        .addFilterBefore(
            new AuditAccessTokenFilter(tokens), UsernamePasswordAuthenticationFilter.class)
        .build();
  }
}
