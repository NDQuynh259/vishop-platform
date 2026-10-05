package com.vishop.tenant.application.query;

import com.vishop.common.domain.cqrs.QueryHandler;
import com.vishop.common.domain.exception.EntityNotFoundException;
import com.vishop.tenant.application.dto.TenantResponseDto;
import com.vishop.tenant.domain.TenantRepositoryPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GetTenantByIdQueryHandler implements QueryHandler<GetTenantByIdQuery, TenantResponseDto> {

    private final TenantRepositoryPort tenantRepository;

    public GetTenantByIdQueryHandler(TenantRepositoryPort tenantRepository) {
        this.tenantRepository = tenantRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public TenantResponseDto handle(GetTenantByIdQuery query) {
        return tenantRepository.findById(query.tenantId())
                .map(TenantResponseDto::from)
                .orElseThrow(() -> new EntityNotFoundException("Tenant", query.tenantId()));
    }
}
