package io.github.coreplatform.common.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AggregateRootTest {

    private record TestDomainEvent(UUID eventId, UUID aggregateId) implements DomainEvent {
        @Override
        public Instant occurredAt() { return Instant.now(); }
        @Override
        public String eventType() { return "test.event"; }
        @Override
        public String aggregateType() { return "TestAggregate"; }
    }

    private static class SampleAggregate extends AggregateRoot<UUID> {
        public SampleAggregate(UUID id) {
            super(id);
        }

        public void doSomething() {
            registerEvent(new TestDomainEvent(UuidV7.nextId(), getId()));
        }
    }

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
}
