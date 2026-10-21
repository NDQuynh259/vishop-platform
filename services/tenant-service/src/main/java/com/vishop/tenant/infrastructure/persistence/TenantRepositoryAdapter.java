package com.vishop.tenant.infrastructure.persistence;

import com.vishop.tenant.domain.Tenant;
import com.vishop.tenant.domain.TenantRepositoryPort;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

/**
 * Adapter implementing the domain port {@link TenantRepositoryPort}
 * using Spring Data JPA.
 */
@Component
public class TenantRepositoryAdapter implements TenantRepositoryPort {

    private final SpringDataTenantRepository repository;

    // #region TenantRepositoryAdapter
    public TenantRepositoryAdapter(SpringDataTenantRepository repository) {
        this.repository = repository;
    }
    // #endregion

    // #region save
    @Override
    public Tenant save(Tenant tenant) {
        TenantJpaEntity entity = TenantJpaEntity.fromDomain(tenant);
        TenantJpaEntity saved = repository.save(entity);
        return saved.toDomain();
    }
    // #endregion

    // #region findById
    @Override
    public Optional<Tenant> findById(UUID id) {
        return repository.findById(id).map(TenantJpaEntity::toDomain);
    }
    // #endregion

    // #region findByCode
    @Override
    public Optional<Tenant> findByCode(String code) {
        return repository.findByCode(code).map(TenantJpaEntity::toDomain);
    }
    // #endregion

    // #region existsByCode
    @Override
    public boolean existsByCode(String code) {
        return repository.existsByCode(code);
    }
    // #endregion

    // #region existsById
    @Override
    public boolean existsById(UUID id) {
        return repository.existsById(id);
    }
    // #endregion

    // #region delete
    @Override
    public void delete(Tenant tenant) {
        repository.deleteById(tenant.getId());
    }
    // #endregion

    // #region findTenants
    @Override
    public List<Tenant> findTenants(Instant cursorCreatedAt, UUID cursorId, int limit) {
        return repository.findTenantsCursor(cursorCreatedAt, cursorId, PageRequest.of(0, limit))
                .stream()
                .map(TenantJpaEntity::toDomain)
                .toList();
    }
    // #endregion
}
