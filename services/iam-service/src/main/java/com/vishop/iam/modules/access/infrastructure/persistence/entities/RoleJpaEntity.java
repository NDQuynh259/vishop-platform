package com.vishop.iam.modules.access.infrastructure.persistence.entities;

import com.vishop.iam.modules.access.domain.entity.Role;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "roles")
public class RoleJpaEntity {
  @Id private UUID id;

  @Column(name = "tenant_id", nullable = false)
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

  @Version private Long version;

  protected RoleJpaEntity() {}

  public static RoleJpaEntity create(UUID id, UUID tenant, String name, Set<String> permissions) {
    var entity = new RoleJpaEntity();
    entity.id = id;
    entity.tenantId = tenant;
    entity.name = name;
    entity.permissions.addAll(permissions);
    entity.createdAt = Instant.now();
    entity.updatedAt = entity.createdAt;
    return entity;
  }

  public void update(String name, Set<String> grants) {
    this.name = name;
    permissions.clear();
    permissions.addAll(grants);
    updatedAt = Instant.now();
  }

  public Role toDomain() {
    return new Role(id, tenantId, name, builtinKey, requireTwoFactor, permissions);
  }
}
