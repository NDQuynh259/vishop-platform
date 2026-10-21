package io.github.coreplatform.common.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AggregateRootTest {

    private record TestDomainEvent(UUID eventId, UUID aggregateId) implements DomainEvent {
        // #region occurredAt
        @Override
        public Instant occurredAt() { return Instant.now(); }
        // #endregion
        // #region eventType
        @Override
        public String eventType() { return "test.event"; }
        // #endregion
        // #region aggregateType
        @Override
        public String aggregateType() { return "TestAggregate"; }
        // #endregion
    }

    private static class SampleAggregate extends AggregateRoot<UUID> {
        // #region SampleAggregate
        public SampleAggregate(UUID id) {
            super(id);
        }
        // #endregion

        // #region doSomething
        public void doSomething() {
            registerEvent(new TestDomainEvent(UuidV7.nextId(), getId()));
        }
        // #endregion
    }

    // #region shouldRegisterAndPullDomainEvents
    @Test
    void shouldRegisterAndPullDomainEvents() {
        UUID id = UuidV7.nextId();
        SampleAggregate aggregate = new SampleAggregate(id);

        assertTrue(aggregate.getDomainEvents().isEmpty());

        aggregate.doSomething();
        assertEquals(1, aggregate.getDomainEvents().size());

        List<DomainEvent> pulled = aggregate.pullDomainEvents();
        assertEquals(1, pulled.size());
        assertEquals(id, pulled.get(0).aggregateId());

        // After pulling, internal state is cleared
        assertTrue(aggregate.getDomainEvents().isEmpty());
    }
    // #endregion
}
