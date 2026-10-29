package com.vishop.iam.modules.tenant.infrastructure.integration;

import com.vishop.iam.modules.identity.application.api.IdentityApi;
import com.vishop.iam.modules.tenant.domain.port.UserDirectoryPort;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class IdentityUserAdapter implements UserDirectoryPort {
  private final IdentityApi identities;

  public IdentityUserAdapter(IdentityApi identities) {
    this.identities = identities;
  }

  public boolean isActive(UUID userId) {
    return identities.isActive(userId);
  }
}
