package com.vishop.iam.modules.access.infrastructure.integration;

import com.vishop.iam.modules.access.domain.port.UserActivityPort;
import com.vishop.iam.modules.identity.application.api.IdentityApi;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class IdentityUserActivityAdapter implements UserActivityPort {
  private final IdentityApi identities;

  public IdentityUserActivityAdapter(IdentityApi identities) {
    this.identities = identities;
  }

  public boolean isActive(UUID userId) {
    return identities.isActive(userId);
  }
}
