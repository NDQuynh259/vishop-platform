package com.vishop.iam.tenant.infrastructure.persistence.entities;

import com.vishop.iam.tenant.domain.entities.Tenant;
import com.vishop.iam.tenant.domain.valueobjects.TenantCode;
import com.vishop.iam.tenant.domain.valueobjects.TenantStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "tenants")
public class TenantJpaEntity {
  @Id private UUID id;

  @Column(nullable = false, updatable = false)
  private String code;

  @Column(nullable = false)
  private String name;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private TenantStatus status;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Version private Long version;

  protected TenantJpaEntity() {}

  public static TenantJpaEntity fromDomain(Tenant tenant) {
    var entity = new TenantJpaEntity();
    entity.id = tenant.id();
    entity.code = tenant.code().value();
    entity.name = tenant.name();
    entity.status = tenant.status();
    entity.createdAt = tenant.createdAt();
    entity.updatedAt = Instant.now();
    entity.version = tenant.getVersion();
    return entity;
  }

  public Tenant toDomain() {
    return Tenant.reconstitute(id, new TenantCode(code), name, status, createdAt, version);
  }
}
