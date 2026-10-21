package io.github.coreplatform.common.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.coreplatform.common.domain.AggregateRoot;
import io.github.coreplatform.common.domain.DomainEvent;
import io.github.coreplatform.common.events.EventEnvelope;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service to store pending domain events from Aggregate Roots into the transactional outbox table.
 * Executed in the SAME database transaction as aggregate persistence.
 */
@Service
public class OutboxService {

    private static final Logger log = LoggerFactory.getLogger(OutboxService.class);

    private final OutboxEventRepository repository;
    private final ObjectMapper objectMapper;

    // #region OutboxService
    public OutboxService(OutboxEventRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }
    // #endregion

    // #region saveEventsFor
    /**
     * Pulls domain events from the aggregate root, wraps each into an {@link EventEnvelope},
     * and persists it into the outbox table.
     *
     * @param aggregateRoot aggregate containing pending domain events
     * @param producer service name (e.g. "tenant-service")
     * @param correlationId correlation identifier for tracing
     * @param causationId causation identifier
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void saveEventsFor(AggregateRoot<?> aggregateRoot, String producer, UUID correlationId, UUID causationId) {
        List<DomainEvent> events = aggregateRoot.pullDomainEvents();
        if (events.isEmpty()) {
            return;
        }

        for (DomainEvent event : events) {
            try {
                EventEnvelope envelope = EventEnvelope.fromDomainEvent(
                        event, producer, correlationId, causationId, objectMapper);
                String json = objectMapper.writeValueAsString(envelope);
                OutboxEventEntity entity = OutboxEventEntity.fromEnvelope(envelope, json);
                repository.save(entity);
                log.debug("Enqueued domain event [{}] of type [{}] into outbox", entity.getId(), entity.getEventType());
            } catch (JsonProcessingException e) {
                log.error("Failed to serialize domain event [{}] for outbox", event.eventId(), e);
                throw new IllegalStateException("Failed to serialize outbox event payload", e);
            }
        }
    }
    // #endregion

    // #region saveEventsFor
    /**
     * Overload using null correlation/causation if not in an active trace.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void saveEventsFor(AggregateRoot<?> aggregateRoot, String producer) {
        saveEventsFor(aggregateRoot, producer, null, null);
    }
    // #endregion
}
