package io.github.coreplatform.common.security.audit;

import io.github.coreplatform.common.security.context.CommonUserDetails;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SpringSecurityAuditorAwareTest {

    private final SpringSecurityAuditorAware auditorAware = new SpringSecurityAuditorAware();

    // #region tearDown
    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }
    // #endregion

    // #region shouldReturnUserIdWhenAuthenticated
    @Test
    @DisplayName("Should return userId as current auditor when authenticated")
    void shouldReturnUserIdWhenAuthenticated() {
        UUID userId = UUID.randomUUID();
        CommonUserDetails user = new CommonUserDetails(userId, UUID.randomUUID(), Set.of(), List.of());

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities())
        );

        assertThat(auditorAware.getCurrentAuditor()).contains(userId);
    }
    // #endregion

    // #region shouldReturnEmptyWhenUnauthenticated
    @Test
    @DisplayName("Should return empty optional when not authenticated")
    void shouldReturnEmptyWhenUnauthenticated() {
        assertThat(auditorAware.getCurrentAuditor()).isEmpty();
    }
    // #endregion
}
