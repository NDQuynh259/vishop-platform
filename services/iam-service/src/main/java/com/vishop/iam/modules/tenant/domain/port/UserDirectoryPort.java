package com.vishop.iam.modules.tenant.domain.port;

import java.util.UUID;

public interface UserDirectoryPort {
  boolean isActive(UUID userId);
}
