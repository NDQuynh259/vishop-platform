package com.vishop.iam.access.domain.ports;

import java.util.UUID;

public interface UserActivityPort {
  boolean isActive(UUID userId);
}
