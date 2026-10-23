package com.vishop.identity.infrastructure.security;

import io.github.coreplatform.common.security.jwt.JwtTokenProvider;
import com.vishop.identity.application.port.TokenIssuerPort;
import com.vishop.identity.domain.entities.User;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Adapter implementing TokenIssuerPort using common-security's JwtTokenProvider.
 */
@Component
public class JwtTokenIssuerAdapter implements TokenIssuerPort {

    private final JwtTokenProvider jwtTokenProvider;

    public JwtTokenIssuerAdapter(JwtTokenProvider jwtTokenProvider) {
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @Override
    public String generateAccessToken(User user, List<String> roles, List<String> permissions) {
        return jwtTokenProvider.generateAccessToken(
                user.getId(),
                user.getTenantId(),
                user.getEmail(),
                user.getFullName(),
                roles,
                permissions
        );
    }

    @Override
    public long getExpirationSeconds() {
        return jwtTokenProvider.getExpirationMs() / 1000;
    }
}
