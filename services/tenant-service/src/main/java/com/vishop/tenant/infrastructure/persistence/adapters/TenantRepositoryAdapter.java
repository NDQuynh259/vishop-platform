package com.vishop.tenant.infrastructure.persistence.adapters;

import com.vishop.tenant.domain.entities.Tenant;
import com.vishop.tenant.domain.ports.TenantRepositoryPort;
import com.vishop.tenant.domain.valueobjects.TenantStatus;
import com.vishop.tenant.infrastructure.persistence.entities.TenantJpaEntity;
import com.vishop.tenant.infrastructure.persistence.repositories.SpringDataTenantRepository;
import com.vishop.tenant.infrastructure.persistence.repositories.TenantSpecifications;
import io.github.coreplatform.common.domain.pagination.OffsetPagination;
import io.github.coreplatform.common.domain.pagination.PagedResult;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
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
    public List<Tenant> findTenants(TenantStatus status, String search, Instant cursorCreatedAt, UUID cursorId,
            int limit) {
        Sort sort = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));
        PageRequest pageRequest = PageRequest.of(0, limit, sort);
        Specification<TenantJpaEntity> spec = TenantSpecifications.withFilter(status, search, cursorCreatedAt,
                cursorId);

        return repository.findAll(spec, pageRequest)
                .stream()
                .map(TenantJpaEntity::toDomain)
                .toList();
    }

    @Override
    public List<Tenant> findTenants(Instant cursorCreatedAt, UUID cursorId, int limit) {
        return findTenants(null, null, cursorCreatedAt, cursorId, limit);
    }
    // #endregion

    // #region findTenantsOffset
    @Override
    public PagedResult<Tenant> findTenantsOffset(TenantStatus status, String search, OffsetPagination pagination) {
        Sort sort = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));
        PageRequest pageRequest = PageRequest.of(pagination.pageIndex(), pagination.limit(), sort);
        Specification<TenantJpaEntity> spec = TenantSpecifications.withFilter(status, search, null, null);

        Page<TenantJpaEntity> page = repository.findAll(spec, pageRequest);
        List<Tenant> tenants = page.getContent().stream()
                .map(TenantJpaEntity::toDomain)
                .toList();
        return PagedResult.ofOffset(tenants, pagination.page(), pagination.limit(), page.getTotalElements());
    }
    // #endregion
}
