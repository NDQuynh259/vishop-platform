package com.vishop.iam.modules.identity.infrastructure.persistence.entities;

import com.vishop.iam.modules.identity.domain.entity.User;
import com.vishop.iam.modules.identity.domain.valueobject.SystemRole;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserJpaEntity {
  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @Column(nullable = false)
  private String email;

  @Column(name = "password_hash", nullable = false)
  private String passwordHash;

  @Column(name = "full_name")
  private String fullName;

  @Enumerated(EnumType.STRING)
  @Column(name = "system_role", nullable = false, length = 30)
  private SystemRole systemRole;

  @Column(nullable = false, length = 30)
  private String status;

  @Column(name = "last_tenant_id")
  private UUID lastTenantId;

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

  public static UserJpaEntity create(UUID id, String email, String hash) {
    var entity = new UserJpaEntity();
    entity.setId(id);
    entity.email = email;
    entity.passwordHash = hash;
    entity.systemRole = SystemRole.USER;
    entity.status = "ACTIVE";
    return entity;
  }

  public User toDomain() {
    return new User(
        getId(), email, passwordHash, fullName, systemRole, status, getCreatedAt(), lastTenantId);
  }
}
