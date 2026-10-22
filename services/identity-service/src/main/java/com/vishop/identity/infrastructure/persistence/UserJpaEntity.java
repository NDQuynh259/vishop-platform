package com.vishop.identity.infrastructure.persistence;

import com.vishop.identity.domain.aggregate.User;
import com.vishop.identity.domain.valueobject.UserStatus;
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
@Table(name = "users")
public class UserJpaEntity {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Column(nullable = false)
    private String role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    public UserJpaEntity() {}

    // #region toDomain
    public User toDomain() {
        return User.reconstitute(
                this.id,
                this.tenantId,
                this.email,
                this.passwordHash,
                this.fullName,
                this.role,
                this.status,
                this.createdAt,
                this.updatedAt,
                this.version
        );
    }
    // #endregion

    // #region fromDomain
    public static UserJpaEntity fromDomain(User domain) {
        UserJpaEntity entity = new UserJpaEntity();
        entity.id = domain.getId();
        entity.tenantId = domain.getTenantId();
        entity.email = domain.getEmail();
        entity.passwordHash = domain.getPasswordHash();
        entity.fullName = domain.getFullName();
        entity.role = domain.getRole();
        entity.status = domain.getStatus();
        entity.createdAt = domain.getCreatedAt();
        entity.updatedAt = domain.getUpdatedAt();
        entity.version = domain.getVersion();
        return entity;
    }
    // #endregion

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getFullName() {
        return fullName;
    }

    public String getRole() {
        return role;
    }

    public UserStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Long getVersion() {
        return version;
    }
}
