package com.vishop.identity.domain.event;

import io.github.coreplatform.common.domain.DomainEvent;
import io.github.coreplatform.common.domain.UuidV7;
import java.time.Instant;
import java.util.UUID;

/**
 * Domain Event emitted when a user successfully authenticates.
 */
public record UserLoggedInEvent(
        UUID eventId,
        Instant occurredAt,
        UUID aggregateId,
        UUID tenantId,
        String email
) implements DomainEvent {

    public static UserLoggedInEvent of(UUID userId, UUID tenantId, String email) {
        return new UserLoggedInEvent(
                UuidV7.nextId(),
                Instant.now(),
                userId,
                tenantId,
                email
        );
    }

    @Override
    public String eventType() {
        return "identity.user.logged_in";
    }

    @Override
    public String aggregateType() {
        return "User";
    }
}
