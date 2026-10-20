package io.github.coreplatform.common.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Marker interface for all domain events.
 * A domain event represents something that took place in the domain in the past.
 */
public interface DomainEvent {

    /** Unique event identifier (UUID v7). */
    UUID eventId();

    /** Timestamp when the domain event occurred. */
    Instant occurredAt();

    /** Name / type of the event (e.g. "tenant.created", "user.registered"). */
    String eventType();

    /** Type of aggregate emitting this event (e.g. "Tenant", "User"). */
    String aggregateType();

    /** Identifier of the aggregate emitting this event. */
    UUID aggregateId();

    /** Optional tenant identifier for multi-tenant isolation. */
    default UUID tenantId() {
        return null;
    }
}
