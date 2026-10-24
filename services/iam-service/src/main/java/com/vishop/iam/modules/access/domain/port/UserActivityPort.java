package com.vishop.iam.modules.access.domain.port;

import java.util.UUID;

public interface UserActivityPort {
  boolean isActive(UUID userId);
}
