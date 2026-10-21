package com.vishop.tenant.domain.event;

import io.github.coreplatform.common.domain.DomainEvent;
import io.github.coreplatform.common.domain.UuidV7;
import com.vishop.tenant.domain.TenantStatus;
import java.time.Instant;
import java.util.UUID;

/**
 * Domain event emitted when a new Tenant is created.
 */
public record TenantCreatedEvent(
        UUID eventId,
        UUID tenantId,
        String code,
        String name,
        TenantStatus status,
        Instant occurredAt) implements DomainEvent {

    // #region of
    public static TenantCreatedEvent of(UUID tenantId, String code, String name, TenantStatus status) {
        return new TenantCreatedEvent(UuidV7.nextId(), tenantId, code, name, status, Instant.now());
    }
    // #endregion

    // #region eventType
    @Override
    public String eventType() {
        return "tenant.created";
    }
    // #endregion

    // #region aggregateType
    @Override
    public String aggregateType() {
        return "Tenant";
    }
    // #endregion

    // #region aggregateId
    @Override
    public UUID aggregateId() {
        return tenantId;
    }
    // #endregion
}
