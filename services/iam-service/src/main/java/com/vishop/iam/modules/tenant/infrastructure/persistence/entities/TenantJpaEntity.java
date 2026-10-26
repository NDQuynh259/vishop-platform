package com.vishop.iam.modules.tenant.infrastructure.persistence.entities;

import com.vishop.iam.modules.tenant.domain.entity.Tenant;
import com.vishop.iam.modules.tenant.domain.valueobject.TenantCode;
import com.vishop.iam.modules.tenant.domain.valueobject.TenantStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "tenants")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TenantJpaEntity {
  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

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

  @Column(name = "deleted_at")
  private Instant deletedAt;

  @Version
  @Column(name = "version")
  private Long version;

  @PrePersist
  private void beforeInsert() {
    if (createdAt == null) createdAt = Instant.now();
    if (updatedAt == null) updatedAt = createdAt;
  }

  @PreUpdate
  private void beforeUpdate() {
    updatedAt = Instant.now();
  }

  public static TenantJpaEntity fromDomain(Tenant tenant) {
    var entity = new TenantJpaEntity();
    entity.setId(tenant.id());
    entity.code = tenant.code().value();
    entity.name = tenant.name();
    entity.status = tenant.status();
    entity.setCreatedAt(tenant.createdAt());
    entity.setVersion(tenant.getVersion());
    return entity;
  }

  public Tenant toDomain() {
    return Tenant.reconstitute(
        getId(), new TenantCode(code), name, status, getCreatedAt(), getVersion());
  }
}
