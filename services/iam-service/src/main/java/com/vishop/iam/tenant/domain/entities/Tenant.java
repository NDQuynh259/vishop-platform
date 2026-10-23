package com.vishop.iam.tenant.domain.entities;

import com.vishop.iam.tenant.domain.event.TenantCreatedEvent;
import com.vishop.iam.tenant.domain.event.TenantRenamedEvent;
import com.vishop.iam.tenant.domain.event.TenantStatusChangedEvent;
import com.vishop.iam.tenant.domain.valueobjects.TenantCode;
import com.vishop.iam.tenant.domain.valueobjects.TenantStatus;
import io.github.coreplatform.common.domain.AggregateRoot;
import io.github.coreplatform.common.domain.UuidV7;
import io.github.coreplatform.common.domain.exception.BadRequestException;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class Tenant extends AggregateRoot<UUID> {
  private final TenantCode code;
  private String name;
  private TenantStatus status;
  private final Instant createdAt;

  private Tenant(
      UUID id, TenantCode code, String name, TenantStatus status, Instant createdAt, Long version) {
    super(id, version);
    this.code = Objects.requireNonNull(code);
    this.name = validName(name);
    this.status = Objects.requireNonNull(status);
    this.createdAt = Objects.requireNonNull(createdAt);
  }

  public static Tenant create(TenantCode code, String name, UUID ownerId) {
    var tenant = new Tenant(UuidV7.nextId(), code, name, TenantStatus.ACTIVE, Instant.now(), null);
    tenant.registerEvent(
        new TenantCreatedEvent(UuidV7.nextId(), Instant.now(), tenant.id(), ownerId, code.value()));
    return tenant;
  }

  public static Tenant reconstitute(
      UUID id, TenantCode code, String name, TenantStatus status, Instant createdAt, Long version) {
    return new Tenant(id, code, name, status, createdAt, version);
  }

  public void rename(String name, UUID actorId) {
    this.name = validName(name);
    registerEvent(new TenantRenamedEvent(UuidV7.nextId(), Instant.now(), id(), actorId));
  }

  public void changeStatus(TenantStatus next, UUID actorId) {
    Objects.requireNonNull(next);
    if (status == TenantStatus.ARCHIVED && next != TenantStatus.ARCHIVED)
      throw new BadRequestException("Archived tenants cannot be reactivated");
    status = next;
    registerEvent(new TenantStatusChangedEvent(UuidV7.nextId(), Instant.now(), id(), actorId));
  }

  private static String validName(String name) {
    if (name == null || name.isBlank() || name.trim().length() > 200)
      throw new BadRequestException("Invalid tenant name");
    return name.trim();
  }

  public UUID id() {
    return getId();
  }

  public TenantCode code() {
    return code;
  }

  public String name() {
    return name;
  }

  public TenantStatus status() {
    return status;
  }

  public Instant createdAt() {
    return createdAt;
  }
}
