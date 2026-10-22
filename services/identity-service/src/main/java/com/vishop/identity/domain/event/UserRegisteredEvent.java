package com.vishop.identity.domain.event;

import io.github.coreplatform.common.domain.DomainEvent;
import io.github.coreplatform.common.domain.UuidV7;
import java.time.Instant;
import java.util.UUID;

/**
 * Domain Event emitted when a new user registers or is created in a tenant.
 */
public record UserRegisteredEvent(
        UUID eventId,
        Instant occurredAt,
        UUID aggregateId,
        UUID tenantId,
        String email,
        String fullName,
        String role
) implements DomainEvent {

    public static UserRegisteredEvent of(UUID userId, UUID tenantId, String email, String fullName, String role) {
        return new UserRegisteredEvent(
                UuidV7.nextId(),
                Instant.now(),
                userId,
                tenantId,
                email,
                fullName,
                role
        );
    }

    @Override
    public String eventType() {
        return "identity.user.registered";
    }

    @Override
    public String aggregateType() {
        return "User";
    }
}
