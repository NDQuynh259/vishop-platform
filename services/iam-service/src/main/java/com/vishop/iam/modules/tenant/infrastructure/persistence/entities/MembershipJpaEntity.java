package com.vishop.iam.modules.tenant.infrastructure.persistence.entities;

import com.vishop.iam.modules.tenant.domain.entity.Membership;
import com.vishop.iam.modules.tenant.domain.entity.Role;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "tenant_members")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MembershipJpaEntity {
  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @Column(name = "tenant_id", nullable = false, updatable = false)
  private UUID tenantId;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Column(name = "role_id", nullable = false)
  private UUID roleId;

  @Column(nullable = false, length = 30)
  private String status;

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

  public static MembershipJpaEntity create(UUID id, UUID tenant, UUID user, UUID role) {
    var entity = new MembershipJpaEntity();
    entity.setId(id);
    entity.tenantId = tenant;
    entity.userId = user;
    entity.roleId = role;
    entity.status = "ACTIVE";
    return entity;
  }

  public void update(UUID role, String status) {
    this.roleId = role;
    this.status = status;
  }

  public Membership toDomain(Role role) {
    return new Membership(
        getId(),
        tenantId,
        userId,
        roleId,
        role.builtinKey() != null ? role.builtinKey() : role.name(),
        status,
        role.requireTwoFactor(),
        role.permissions());
  }
}
