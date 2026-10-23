package com.vishop.tenant.infrastructure.persistence;

import com.vishop.tenant.domain.Tenant;
import com.vishop.tenant.domain.TenantCode;
import com.vishop.tenant.domain.TenantStatus;
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

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "code", nullable = false, unique = true, length = 50)
    private String code;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private TenantStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version")
    private Long version;

    // #region TenantJpaEntity
    protected TenantJpaEntity() {
    }
    // #endregion

    // #region TenantJpaEntity
    public TenantJpaEntity(UUID id, String code, String name, TenantStatus status,
                           Instant createdAt, Instant updatedAt, Long version) {
        this.id = id;
        this.code = code;
        this.name = name;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.version = version;
    }
    // #endregion

    // #region fromDomain
    public static TenantJpaEntity fromDomain(Tenant tenant) {
        return new TenantJpaEntity(
                tenant.getId(),
                tenant.getCode().value(),
                tenant.getName(),
                tenant.getStatus(),
                tenant.getCreatedAt(),
                tenant.getUpdatedAt(),
                tenant.getVersion()
        );
    }
    // #endregion

    // #region toDomain
    public Tenant toDomain() {
        return Tenant.reconstitute(
                id,
                TenantCode.of(code),
                name,
                status,
                createdAt,
                updatedAt,
                version
        );
    }
    // #endregion

    // #region getId
    public UUID getId() { return id; }
    // #endregion
    // #region getCode
    public String getCode() { return code; }
    // #endregion
    // #region getName
    public String getName() { return name; }
    // #endregion
    // #region getStatus
    public TenantStatus getStatus() { return status; }
    // #endregion
    // #region getCreatedAt
    public Instant getCreatedAt() { return createdAt; }
    // #endregion
    // #region getUpdatedAt
    public Instant getUpdatedAt() { return updatedAt; }
    // #endregion
    // #region getVersion
    public Long getVersion() { return version; }
    // #endregion
}
