package com.vishop.tenant.application.query;

import io.github.coreplatform.common.domain.cqrs.QueryHandler;
import io.github.coreplatform.common.domain.pagination.CursorPagination;
import io.github.coreplatform.common.domain.pagination.OffsetPagination;
import io.github.coreplatform.common.domain.pagination.PagedResult;
import com.vishop.tenant.application.dto.TenantResponseDto;
import com.vishop.tenant.domain.Tenant;
import com.vishop.tenant.domain.TenantRepositoryPort;
import java.time.Instant;
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
        if (query.isCursor()) {
            CursorPagination cursor = query.pagination().decodeCursor();
            Instant cursorCreatedAt = cursor != null ? cursor.createdAt() : null;
            UUID cursorId = cursor != null ? cursor.idAsUuid() : null;

            int limit = query.pagination().limit();
            // Fetch limit + 1 to determine if there is a next page
            int fetchSize = limit + 1;
            List<Tenant> tenants = tenantRepository.findTenants(
                    query.filter().status(),
                    query.filter().search(),
                    cursorCreatedAt,
                    cursorId,
                    fetchSize);

            boolean hasNextPage = tenants.size() > limit;
            List<Tenant> pageTenants = hasNextPage ? tenants.subList(0, limit) : tenants;

            String nextCursor = null;
            if (hasNextPage && !pageTenants.isEmpty()) {
                Tenant last = pageTenants.get(pageTenants.size() - 1);
                nextCursor = CursorPagination.of(last.getCreatedAt(), last.getId()).encode();
            }

            List<TenantResponseDto> dtoList = pageTenants.stream()
                    .map(TenantResponseDto::from)
                    .toList();

            return PagedResult.ofCursor(dtoList, hasNextPage, nextCursor);
        } else {
            OffsetPagination offset = query.offsetParams() != null ? query.offsetParams() : OffsetPagination.ofDefault();
            PagedResult<Tenant> result = tenantRepository.findTenantsOffset(
                    query.filter().status(),
                    query.filter().search(),
                    offset);

            List<TenantResponseDto> dtoList = result.items().stream()
                    .map(TenantResponseDto::from)
                    .toList();

            return PagedResult.ofOffset(dtoList, result.page(), result.limit(), result.total());
        }
    }
    // #endregion
}
