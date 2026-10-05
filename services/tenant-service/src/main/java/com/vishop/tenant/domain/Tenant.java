package com.vishop.tenant.domain;

import com.vishop.common.domain.AggregateRoot;
import com.vishop.common.domain.UuidV7;
import com.vishop.common.domain.exception.DomainException;
import com.vishop.tenant.domain.event.TenantCreatedEvent;
import com.vishop.tenant.domain.event.TenantStatusChangedEvent;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Tenant Aggregate Root.
 * Manages tenant identity, metadata, status transitions, and domain events.
 */
public class Tenant extends AggregateRoot<UUID> {

    private TenantCode code;
    private String name;
    private TenantStatus status;
    private final Instant createdAt;
    private Instant updatedAt;

    private Tenant(UUID id, TenantCode code, String name, TenantStatus status,
                   Instant createdAt, Instant updatedAt, Long version) {
        super(id, version);
        this.code = Objects.requireNonNull(code, "TenantCode cannot be null");
        this.name = Objects.requireNonNull(name, "Tenant name cannot be null");
        this.status = Objects.requireNonNull(status, "TenantStatus cannot be null");
        this.createdAt = createdAt != null ? createdAt : Instant.now();
        this.updatedAt = updatedAt != null ? updatedAt : this.createdAt;
    }

    /**
     * Factory method to create a brand new Tenant aggregate.
     */
    public static Tenant create(TenantCode code, String name) {
        if (name == null || name.isBlank()) {
            throw new DomainException("invalid_tenant_name", "Tenant name cannot be empty");
        }
        UUID id = UuidV7.nextId();
        Tenant tenant = new Tenant(id, code, name.trim(), TenantStatus.ACTIVE, Instant.now(), Instant.now(), null);
        tenant.registerEvent(TenantCreatedEvent.of(id, code.value(), tenant.name, TenantStatus.ACTIVE));
        return tenant;
    }

    /**
     * Reconstitutes an existing Tenant from persistence layer (no domain events emitted).
     */
    public static Tenant reconstitute(UUID id, TenantCode code, String name, TenantStatus status,
                                      Instant createdAt, Instant updatedAt, Long version) {
        return new Tenant(id, code, name, status, createdAt, updatedAt, version);
    }

    /**
     * Updates tenant display name.
     */
    public void updateName(String newName) {
        if (newName == null || newName.isBlank()) {
            throw new DomainException("invalid_tenant_name", "Tenant name cannot be empty");
        }
        if (this.status == TenantStatus.ARCHIVED) {
            throw new DomainException("tenant_archived", "Cannot update an archived tenant");
        }
        this.name = newName.trim();
        this.updatedAt = Instant.now();
    }

    /**
     * Activates the tenant.
     */
    public void activate() {
        if (this.status == TenantStatus.ACTIVE) {
            return;
        }
        if (this.status == TenantStatus.ARCHIVED) {
            throw new DomainException("tenant_archived", "Cannot activate an archived tenant");
        }
        TenantStatus old = this.status;
        this.status = TenantStatus.ACTIVE;
        this.updatedAt = Instant.now();
        registerEvent(TenantStatusChangedEvent.of(getId(), old, TenantStatus.ACTIVE));
    }

    /**
     * Suspends the tenant.
     */
    public void suspend() {
        if (this.status == TenantStatus.SUSPENDED) {
            return;
        }
        if (this.status == TenantStatus.ARCHIVED) {
            throw new DomainException("tenant_archived", "Cannot suspend an archived tenant");
        }
        TenantStatus old = this.status;
        this.status = TenantStatus.SUSPENDED;
        this.updatedAt = Instant.now();
        registerEvent(TenantStatusChangedEvent.of(getId(), old, TenantStatus.SUSPENDED));
    }

    /**
     * Archives the tenant.
     */
    public void archive() {
        if (this.status == TenantStatus.ARCHIVED) {
            return;
        }
        TenantStatus old = this.status;
        this.status = TenantStatus.ARCHIVED;
        this.updatedAt = Instant.now();
        registerEvent(TenantStatusChangedEvent.of(getId(), old, TenantStatus.ARCHIVED));
    }

    public TenantCode getCode() { return code; }
    public String getName() { return name; }
    public TenantStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
