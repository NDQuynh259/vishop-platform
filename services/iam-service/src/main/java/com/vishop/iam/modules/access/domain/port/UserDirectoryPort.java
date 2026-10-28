package com.vishop.iam.modules.access.domain.port;

import java.util.UUID;

public interface UserDirectoryPort {
  boolean isActive(UUID userId);
}
