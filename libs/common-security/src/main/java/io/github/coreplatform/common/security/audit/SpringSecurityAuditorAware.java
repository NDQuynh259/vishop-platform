package io.github.coreplatform.common.security.audit;

import io.github.coreplatform.common.security.context.SecurityContextHelper;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.AuditorAware;

/**
 * Spring Data JPA AuditorAware implementation that extracts the current userId from the
 * authenticated CommonUserDetails context.
 */
public class SpringSecurityAuditorAware implements AuditorAware<UUID> {

  // #region getCurrentAuditor
  @Override
  public Optional<UUID> getCurrentAuditor() {
    return SecurityContextHelper.getCurrentUserId();
  }
  // #endregion
}
