package com.vishop.identity.infrastructure.config;

import io.github.coreplatform.common.security.filter.InternalHeaderAuthFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class SecurityConfig {

    private final String apiPrefix;

    public SecurityConfig(@Value("${vishop.api-prefix:/api/v1}") String apiPrefix) {
        this.apiPrefix = apiPrefix.endsWith("/") ? apiPrefix.substring(0, apiPrefix.length() - 1) : apiPrefix;
    }

    // #region passwordEncoder
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
    // #endregion

    // #region identitySecurityFilterChain
    @Bean
    public SecurityFilterChain identitySecurityFilterChain(
            HttpSecurity http,
            InternalHeaderAuthFilter authFilter) throws Exception {

        return http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(
                                apiPrefix + "/auth/login",
                                apiPrefix + "/auth/register",
                                apiPrefix + "/auth/refresh",
                                "/actuator/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/v3/api-docs/**"
                        ).permitAll()
                        .anyRequest().authenticated()
                )
                .addFilterBefore(authFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }
    // #endregion
}
