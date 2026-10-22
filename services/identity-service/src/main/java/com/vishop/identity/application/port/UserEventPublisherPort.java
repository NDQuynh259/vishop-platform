package com.vishop.identity.application.port;

import io.github.coreplatform.common.domain.DomainEvent;
import java.util.List;

/**
 * Outbound Port for publishing domain events to message bus or application event listeners.
 */
public interface UserEventPublisherPort {

    void publish(DomainEvent event);

    default void publishAll(List<DomainEvent> events) {
        if (events != null) {
            events.forEach(this::publish);
        }
    }
}
