package com.vishop.identity.infrastructure.messaging;

import io.github.coreplatform.common.domain.DomainEvent;
import com.vishop.identity.application.port.UserEventPublisherPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * Adapter implementing UserEventPublisherPort.
 * Dispatches domain events locally via Spring's ApplicationEventPublisher,
 * from where Kafka Outbox relays or asynchronous event listeners can route them.
 */
@Component
public class SpringUserEventPublisherAdapter implements UserEventPublisherPort {

    private static final Logger log = LoggerFactory.getLogger(SpringUserEventPublisherAdapter.class);

    private final ApplicationEventPublisher applicationEventPublisher;

    public SpringUserEventPublisherAdapter(ApplicationEventPublisher applicationEventPublisher) {
        this.applicationEventPublisher = applicationEventPublisher;
    }

    @Override
    public void publish(DomainEvent event) {
        if (event == null) {
            return;
        }
        log.info("Publishing domain event: type={}, aggregateId={}, occurredAt={}",
                event.eventType(), event.aggregateId(), event.occurredAt());
        applicationEventPublisher.publishEvent(event);
    }
}
