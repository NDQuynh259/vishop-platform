package com.vishop.iam.tenant.domain.event;

import com.vishop.iam.shared.domain.event.IamDomainEvent;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record TenantCreatedEvent(
    UUID eventId, Instant occurredAt, UUID tenantId, UUID ownerId, String code)
    implements IamDomainEvent {
  public String eventType() {
    return "TenantCreated";
  }

  public String aggregateType() {
    return "Tenant";
  }

  public UUID aggregateId() {
    return tenantId;
  }

  public Map<String, ?> payload() {
    return Map.of("ownerId", ownerId, "code", code);
  }
}
