package com.vishop.iam.modules.tenant.application.query;

import com.vishop.iam.modules.tenant.application.dto.TenantListCriteria;
import com.vishop.iam.modules.tenant.application.dto.TenantResponseDto;
import com.vishop.iam.shared.application.dto.AuthenticatedUser;
import io.github.coreplatform.common.domain.cqrs.Query;
import io.github.coreplatform.common.domain.pagination.OffsetPagination;
import io.github.coreplatform.common.domain.pagination.PagedResult;

public record ListMyTenantsQuery(
    AuthenticatedUser actor, OffsetPagination pagination, TenantListCriteria criteria)
    implements Query<PagedResult<TenantResponseDto>> {}
