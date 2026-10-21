package io.github.coreplatform.common.security.audit;

import io.github.coreplatform.common.security.context.SecurityContextHelper;
import org.springframework.data.domain.AuditorAware;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA AuditorAware implementation that extracts the current userId
 * from the authenticated CommonUserDetails context.
 */
public class SpringSecurityAuditorAware implements AuditorAware<UUID> {

    @Override
    public Optional<UUID> getCurrentAuditor() {
        return SecurityContextHelper.getCurrentUserId();
    }
}
