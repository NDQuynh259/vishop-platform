package com.vishop.iam.shared.application.port;

import io.github.coreplatform.common.domain.DomainEvent;
import java.util.Map;
import java.util.UUID;

public interface IamEventPublisherPort {
  void append(String type, String aggregate, UUID id, UUID tenant, Map<String, ?> payload);

  void publish(DomainEvent event);
}
