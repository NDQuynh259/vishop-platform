package com.vishop.iam.modules.access.infrastructure.integration;

import com.vishop.iam.contracts.identity.IdentityApi;
import com.vishop.iam.modules.access.domain.port.UserActivityPort;
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
