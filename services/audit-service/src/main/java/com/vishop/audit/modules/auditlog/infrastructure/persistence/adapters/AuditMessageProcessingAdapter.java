package com.vishop.audit.modules.auditlog.infrastructure.persistence.adapters;

import com.vishop.audit.modules.auditlog.application.port.AuditMessageProcessingPort;
import io.github.coreplatform.common.persistence.idempotency.IdempotentConsumer;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class AuditMessageProcessingAdapter implements AuditMessageProcessingPort {
  private final IdempotentConsumer consumer;

  public AuditMessageProcessingAdapter(IdempotentConsumer consumer) {
    this.consumer = consumer;
  }

  @Override
  public boolean executeOnce(UUID eventId, String eventType, Runnable action) {
    return consumer.executeIfNotProcessed(eventId, "audit-service", eventType, action);
  }
}
