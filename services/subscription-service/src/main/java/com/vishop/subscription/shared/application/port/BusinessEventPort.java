package com.vishop.subscription.shared.application.port;

import java.util.*;

public interface BusinessEventPort {
  void append(String type, UUID aggregateId, UUID tenantId, Map<String, ?> payload);
}
