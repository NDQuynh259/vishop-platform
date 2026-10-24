package io.github.coreplatform.common.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

/** Base JPA entity with tenant isolation, audit timestamps, and optimistic locking. */
@MappedSuperclass
public abstract class BaseTenantJpaEntity {

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @Column(name = "tenant_id", nullable = false, updatable = false)
  private UUID tenantId;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Version
  @Column(name = "version")
  private Long version;

  // #region onPrePersist
  @PrePersist
  protected void onPrePersist() {
    if (createdAt == null) {
      createdAt = Instant.now();
    }
    if (updatedAt == null) {
      updatedAt = createdAt;
    }
  }

  // #endregion

  // #region onPreUpdate
  @PreUpdate
  protected void onPreUpdate() {
    updatedAt = Instant.now();
  }

  // #endregion

  // #region getId
  public UUID getId() {
    return id;
  }

  // #endregion
  // #region setId
  public void setId(UUID id) {
    this.id = id;
  }

  // #endregion
  // #region getTenantId
  public UUID getTenantId() {
    return tenantId;
  }

  // #endregion
  // #region setTenantId
  public void setTenantId(UUID tenantId) {
    this.tenantId = tenantId;
  }

  // #endregion
  // #region getCreatedAt
  public Instant getCreatedAt() {
    return createdAt;
  }

  // #endregion
  // #region setCreatedAt
  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }

  // #endregion
  // #region getUpdatedAt
  public Instant getUpdatedAt() {
    return updatedAt;
  }

  // #endregion
  // #region setUpdatedAt
  public void setUpdatedAt(Instant updatedAt) {
    this.updatedAt = updatedAt;
  }

  // #endregion
  // #region getVersion
  public Long getVersion() {
    return version;
  }

  // #endregion
  // #region setVersion
  public void setVersion(Long version) {
    this.version = version;
  }
  // #endregion
}
