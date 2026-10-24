package com.vishop.iam.modules.access.infrastructure.persistence.entities;

import com.vishop.iam.modules.access.domain.entity.Membership;
import com.vishop.iam.modules.access.domain.entity.Role;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "tenant_members")
public class MembershipJpaEntity {
  @Id private UUID id;

  @Column(name = "tenant_id", nullable = false)
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

  @Version private Long version;

  protected MembershipJpaEntity() {}

  public static MembershipJpaEntity create(UUID id, UUID tenant, UUID user, UUID role) {
    var entity = new MembershipJpaEntity();
    entity.id = id;
    entity.tenantId = tenant;
    entity.userId = user;
    entity.roleId = role;
    entity.status = "ACTIVE";
    entity.createdAt = Instant.now();
    entity.updatedAt = entity.createdAt;
    return entity;
  }

  public UUID roleId() {
    return roleId;
  }

  public void update(UUID role, String status) {
    this.roleId = role;
    this.status = status;
    updatedAt = Instant.now();
  }

  public Membership toDomain(Role role) {
    return new Membership(
        id,
        tenantId,
        userId,
        roleId,
        role.builtinKey() != null ? role.builtinKey() : role.name(),
        status,
        role.requireTwoFactor(),
        role.permissions());
  }
}
