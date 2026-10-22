package io.github.coreplatform.common.security.jwt;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
public class JwtAutoConfiguration {

    // #region jwtTokenProvider
    @Bean
    @ConditionalOnMissingBean
    public JwtTokenProvider jwtTokenProvider(
            @Value("${vishop.security.jwt.secret:vishop-platform-super-secret-key-that-is-at-least-256-bits-long}")
            String secret,
            @Value("${vishop.security.jwt.expiration-ms:86400000}")
            long expirationMs) {
        return new JwtTokenProvider(secret, expirationMs);
    }
    // #endregion
}
