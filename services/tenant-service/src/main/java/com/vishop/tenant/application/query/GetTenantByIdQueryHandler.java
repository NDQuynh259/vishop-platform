package com.vishop.tenant.application.query;

import io.github.coreplatform.common.domain.cqrs.QueryHandler;
import io.github.coreplatform.common.domain.exception.EntityNotFoundException;
import com.vishop.tenant.application.dto.TenantResponseDto;
import com.vishop.tenant.domain.ports.TenantRepositoryPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GetTenantByIdQueryHandler implements QueryHandler<GetTenantByIdQuery, TenantResponseDto> {

    private final TenantRepositoryPort tenantRepository;

    // #region GetTenantByIdQueryHandler
    public GetTenantByIdQueryHandler(TenantRepositoryPort tenantRepository) {
        this.tenantRepository = tenantRepository;
    }
    // #endregion

    // #region handle
    @Override
    @Transactional(readOnly = true)
    public TenantResponseDto handle(GetTenantByIdQuery query) {
        return tenantRepository.findById(query.tenantId())
                .map(TenantResponseDto::from)
                .orElseThrow(() -> new EntityNotFoundException("Tenant", query.tenantId()));
    }
    // #endregion
}
