package io.github.coreplatform.common.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Marker interface for all domain events.
 * A domain event represents something that took place in the domain in the past.
 */
public interface DomainEvent {

    // #region eventId
    /** Unique event identifier (UUID v7). */
    UUID eventId();
    // #endregion

    // #region occurredAt
    /** Timestamp when the domain event occurred. */
    Instant occurredAt();
    // #endregion

    // #region eventType
    /** Name / type of the event (e.g. "tenant.created", "user.registered"). */
    String eventType();
    // #endregion

    // #region aggregateType
    /** Type of aggregate emitting this event (e.g. "Tenant", "User"). */
    String aggregateType();
    // #endregion

    // #region aggregateId
    /** Identifier of the aggregate emitting this event. */
    UUID aggregateId();
    // #endregion

    // #region tenantId
    /** Optional tenant identifier for multi-tenant isolation. */
    default UUID tenantId() {
        return null;
    }
    // #endregion
}
