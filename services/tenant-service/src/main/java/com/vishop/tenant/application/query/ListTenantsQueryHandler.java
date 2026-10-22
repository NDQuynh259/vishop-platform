package com.vishop.tenant.application.query;

import io.github.coreplatform.common.domain.cqrs.QueryHandler;
import io.github.coreplatform.common.domain.pagination.CursorPagination;
import io.github.coreplatform.common.domain.pagination.PagedResult;
import com.vishop.tenant.application.dto.TenantResponseDto;
import com.vishop.tenant.domain.Tenant;
import com.vishop.tenant.domain.TenantRepositoryPort;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ListTenantsQueryHandler implements QueryHandler<ListTenantsQuery, PagedResult<TenantResponseDto>> {

    private final TenantRepositoryPort tenantRepository;

    // #region ListTenantsQueryHandler
    public ListTenantsQueryHandler(TenantRepositoryPort tenantRepository) {
        this.tenantRepository = tenantRepository;
    }
    // #endregion

    // #region handle
    @Override
    @Transactional(readOnly = true)
    public PagedResult<TenantResponseDto> handle(ListTenantsQuery query) {
        Instant cursorCreatedAt = null;
        UUID cursorId = null;

        if (query.cursor() != null && !query.cursor().isBlank()) {
            CursorPagination cursor = CursorPagination.decode(query.cursor());
            if (cursor != null) {
                cursorCreatedAt = cursor.createdAt();
                cursorId = cursor.idAsUuid();
            }
        }

        // Fetch limit + 1 to determine if there is a next page
        int fetchSize = query.limit() + 1;
        List<Tenant> tenants = tenantRepository.findTenants(cursorCreatedAt, cursorId, fetchSize);

        boolean hasNextPage = tenants.size() > query.limit();
        List<Tenant> pageTenants = hasNextPage ? tenants.subList(0, query.limit()) : tenants;

        String nextCursor = null;
        if (hasNextPage && !pageTenants.isEmpty()) {
            Tenant last = pageTenants.get(pageTenants.size() - 1);
            nextCursor = CursorPagination.of(last.getCreatedAt(), last.getId()).encode();
        }

        List<TenantResponseDto> dtoList = pageTenants.stream()
                .map(TenantResponseDto::from)
                .toList();

        return PagedResult.ofCursor(dtoList, hasNextPage, nextCursor);
    }
    // #endregion
}
