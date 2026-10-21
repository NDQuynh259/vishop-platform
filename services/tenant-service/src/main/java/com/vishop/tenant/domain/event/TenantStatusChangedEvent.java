package com.vishop.tenant.domain.event;

import io.github.coreplatform.common.domain.DomainEvent;
import io.github.coreplatform.common.domain.UuidV7;
import com.vishop.tenant.domain.TenantStatus;
import java.time.Instant;
import java.util.UUID;

/**
 * Domain event emitted when a Tenant's status changes.
 */
public record TenantStatusChangedEvent(
        UUID eventId,
        UUID tenantId,
        TenantStatus oldStatus,
        TenantStatus newStatus,
        Instant occurredAt) implements DomainEvent {

    // #region of
    public static TenantStatusChangedEvent of(UUID tenantId, TenantStatus oldStatus, TenantStatus newStatus) {
        return new TenantStatusChangedEvent(UuidV7.nextId(), tenantId, oldStatus, newStatus, Instant.now());
    }
    // #endregion

    // #region eventType
    @Override
    public String eventType() {
        return "tenant.status_changed";
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
