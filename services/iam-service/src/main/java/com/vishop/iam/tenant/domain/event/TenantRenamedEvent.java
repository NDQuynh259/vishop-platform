package com.vishop.iam.tenant.domain.event;

import com.vishop.iam.shared.domain.event.IamDomainEvent;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record TenantRenamedEvent(UUID eventId, Instant occurredAt, UUID tenantId, UUID actorId)
    implements IamDomainEvent {
  public String eventType() {
    return "TenantRenamed";
  }

  public String aggregateType() {
    return "Tenant";
  }

  public UUID aggregateId() {
    return tenantId;
  }

  public Map<String, ?> payload() {
    return Map.of("actorId", actorId, "subjectId", tenantId);
  }
}
