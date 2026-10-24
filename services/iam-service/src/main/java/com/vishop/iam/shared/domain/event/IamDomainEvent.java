package com.vishop.iam.shared.domain.event;

import io.github.coreplatform.common.domain.DomainEvent;
import java.util.Map;

public interface IamDomainEvent extends DomainEvent {
  Map<String, ?> payload();
}
