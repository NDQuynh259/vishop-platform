package io.github.coreplatform.common.security.config;

import io.github.coreplatform.common.security.audit.SpringSecurityAuditorAware;
import io.github.coreplatform.common.security.authorization.ShopSecurity;
import io.github.coreplatform.common.security.filter.InternalHeaderAuthFilter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Spring Boot AutoConfiguration for Core Platform Security. Provides pre-configured stateless
 * SecurityFilterChain, internal header filter, ShopSecurity SpEL evaluator, and Spring Data
 * AuditorAware.
 */
@AutoConfiguration(after = io.github.coreplatform.common.security.jwt.JwtAutoConfiguration.class)
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
@ConditionalOnClass(name = "jakarta.servlet.Filter")
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class CommonSecurityAutoConfiguration {

  // #region internalHeaderAuthFilter
  @Bean
  @ConditionalOnMissingBean
  public InternalHeaderAuthFilter internalHeaderAuthFilter(
      io.github.coreplatform.common.security.jwt.JwtTokenProvider tokens) {
    return new InternalHeaderAuthFilter(tokens);
  }

  // #endregion

  // #region shopSecurity
  @Bean
  @ConditionalOnMissingBean
  public ShopSecurity shopSecurity() {
    return new ShopSecurity();
  }

  // #endregion

  @Configuration(proxyBeanMethods = false)
  @ConditionalOnClass(AuditorAware.class)
  public static class JpaAuditingConfiguration {

    // #region springSecurityAuditorAware
    @Bean
    @ConditionalOnMissingBean(AuditorAware.class)
    public SpringSecurityAuditorAware springSecurityAuditorAware() {
      return new SpringSecurityAuditorAware();
    }
    // #endregion
  }

  // #region defaultSecurityFilterChain
  @Bean
  @ConditionalOnMissingBean(SecurityFilterChain.class)
  @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
  public SecurityFilterChain defaultSecurityFilterChain(
      HttpSecurity http, InternalHeaderAuthFilter authFilter) throws Exception {

    return http.csrf(AbstractHttpConfigurer::disable)
        .cors(Customizer.withDefaults())
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            authorize ->
                authorize
                    .requestMatchers(
                        "/actuator/health",
                        "/actuator/info",
                        "/actuator/prometheus",
                        "/swagger-ui/**",
                        "/swagger-ui.html",
                        "/v3/api-docs/**")
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        .addFilterBefore(authFilter, UsernamePasswordAuthenticationFilter.class)
        .build();
  }
  // #endregion
}
