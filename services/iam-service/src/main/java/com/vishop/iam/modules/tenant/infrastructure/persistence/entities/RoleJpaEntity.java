package com.vishop.iam.modules.tenant.infrastructure.persistence.entities;

import com.vishop.iam.modules.tenant.domain.entity.Role;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "roles")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RoleJpaEntity {
  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @Column(name = "tenant_id", nullable = false, updatable = false)
  private UUID tenantId;

  @Column(nullable = false, length = 100)
  private String name;

  @Column(name = "builtin_key", length = 30)
  private String builtinKey;

  @Column(name = "require_two_factor", nullable = false)
  private boolean requireTwoFactor;

  @ElementCollection
  @CollectionTable(name = "role_permissions", joinColumns = @JoinColumn(name = "role_id"))
  @Column(name = "permission_key", nullable = false, length = 100)
  private Set<String> permissions = new HashSet<>();

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

  public static RoleJpaEntity create(UUID id, UUID tenant, String name, Set<String> permissions) {
    var entity = new RoleJpaEntity();
    entity.setId(id);
    entity.tenantId = tenant;
    entity.name = name;
    entity.permissions.addAll(permissions);
    return entity;
  }

  public void update(String name, Set<String> grants) {
    this.name = name;
    permissions.clear();
    permissions.addAll(grants);
  }

  public Role toDomain() {
    return new Role(getId(), tenantId, name, builtinKey, requireTwoFactor, permissions);
  }
}
