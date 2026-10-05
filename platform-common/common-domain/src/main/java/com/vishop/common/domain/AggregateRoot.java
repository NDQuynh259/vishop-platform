package com.vishop.common.domain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Base class for all DDD Aggregate Roots.
 * Aggregate roots are the main entry point to a cluster of associated objects.
 * They are responsible for maintaining consistency and capturing domain events.
 *
 * @param <ID> type of aggregate identifier
 */
public abstract class AggregateRoot<ID> extends Entity<ID> {

    private final transient List<DomainEvent> domainEvents = new ArrayList<>();
    private Long version;

    protected AggregateRoot(ID id) {
        super(id);
    }

    protected AggregateRoot(ID id, Long version) {
        super(id);
        this.version = version;
    }

    /**
     * Records a new domain event that occurred as a result of an aggregate mutation.
     *
     * @param event the domain event
     */
    protected void registerEvent(DomainEvent event) {
        this.domainEvents.add(Objects.requireNonNull(event, "DomainEvent cannot be null"));
    }

    /**
     * Returns an unmodifiable copy of all recorded domain events and clears internal state.
     * Typically invoked by the repository adapter or outbox relay before/after persistence.
     *
     * @return pending domain events
     */
    public List<DomainEvent> pullDomainEvents() {
        if (domainEvents.isEmpty()) {
            return Collections.emptyList();
        }
        List<DomainEvent> events = new ArrayList<>(this.domainEvents);
        this.domainEvents.clear();
        return Collections.unmodifiableList(events);
    }

    /**
     * Clears all domain events without pulling them.
     */
    public void clearDomainEvents() {
        this.domainEvents.clear();
    }

    /**
     * Read-only view of recorded domain events without clearing.
     */
    public List<DomainEvent> getDomainEvents() {
        return Collections.unmodifiableList(this.domainEvents);
    }

    public Long getVersion() {
        return version;
    }

    protected void setVersion(Long version) {
        this.version = version;
    }
}
