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

    public TenantRepositoryAdapter(SpringDataTenantRepository repository) {
        this.repository = repository;
    }

    @Override
    public Tenant save(Tenant tenant) {
        TenantJpaEntity entity = TenantJpaEntity.fromDomain(tenant);
        TenantJpaEntity saved = repository.save(entity);
        return saved.toDomain();
    }

    @Override
    public Optional<Tenant> findById(UUID id) {
        return repository.findById(id).map(TenantJpaEntity::toDomain);
    }

    @Override
    public Optional<Tenant> findByCode(String code) {
        return repository.findByCode(code).map(TenantJpaEntity::toDomain);
    }

    @Override
    public boolean existsByCode(String code) {
        return repository.existsByCode(code);
    }

    @Override
    public boolean existsById(UUID id) {
        return repository.existsById(id);
    }

    @Override
    public void delete(Tenant tenant) {
        repository.deleteById(tenant.getId());
    }

    @Override
    public List<Tenant> findTenants(Instant cursorCreatedAt, UUID cursorId, int limit) {
        return repository.findTenantsCursor(cursorCreatedAt, cursorId, PageRequest.of(0, limit))
                .stream()
                .map(TenantJpaEntity::toDomain)
                .toList();
    }
}
