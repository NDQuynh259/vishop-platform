package com.vishop.identity.domain.event;

import io.github.coreplatform.common.domain.DomainEvent;
import io.github.coreplatform.common.domain.UuidV7;
import java.time.Instant;
import java.util.UUID;

/**
 * Domain Event emitted when a user account status changes (e.g. suspended, activated).
 */
public record UserStatusChangedEvent(
        UUID eventId,
        Instant occurredAt,
        UUID aggregateId,
        UUID tenantId,
        String previousStatus,
        String newStatus,
        String reason
) implements DomainEvent {

    public static UserStatusChangedEvent of(
            UUID userId,
            UUID tenantId,
            String previousStatus,
            String newStatus,
            String reason) {
        return new UserStatusChangedEvent(
                UuidV7.nextId(),
                Instant.now(),
                userId,
                tenantId,
                previousStatus,
                newStatus,
                reason
        );
    }

    @Override
    public String eventType() {
        return "identity.user.status_changed";
    }

    @Override
    public String aggregateType() {
        return "User";
    }
}
