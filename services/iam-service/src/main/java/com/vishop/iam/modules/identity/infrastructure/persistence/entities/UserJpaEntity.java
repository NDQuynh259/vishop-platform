package com.vishop.iam.modules.identity.infrastructure.persistence.entities;

import com.vishop.iam.modules.identity.domain.entity.User;
import com.vishop.iam.modules.identity.domain.valueobject.SystemRole;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "users")
public class UserJpaEntity {
  @Id private UUID id;

  @Column(nullable = false)
  private String email;

  @Column(name = "password_hash", nullable = false)
  private String passwordHash;

  @Column(name = "full_name", nullable = false)
  private String fullName;

  @Enumerated(EnumType.STRING)
  @Column(name = "system_role", nullable = false, length = 30)
  private SystemRole systemRole;

  @Column(nullable = false, length = 30)
  private String status;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Version private Long version;

  protected UserJpaEntity() {}

  public static UserJpaEntity create(UUID id, String email, String hash, String name) {
    var entity = new UserJpaEntity();
    entity.id = id;
    entity.email = email;
    entity.passwordHash = hash;
    entity.fullName = name;
    entity.systemRole = SystemRole.USER;
    entity.status = "ACTIVE";
    entity.createdAt = Instant.now();
    entity.updatedAt = entity.createdAt;
    return entity;
  }

  public User toDomain() {
    return new User(id, email, passwordHash, fullName, systemRole, status, createdAt);
  }
}
