package com.vishop.iam.modules.tenant.application.query.tenant;

import com.vishop.iam.modules.tenant.application.dto.TenantResponseDto;
import com.vishop.iam.shared.application.dto.AuthenticatedUser;
import io.github.coreplatform.common.domain.cqrs.Query;
import io.github.coreplatform.common.domain.pagination.ListPagination;
import io.github.coreplatform.common.domain.pagination.PagedResult;

public record ListCmsTenantsQuery(AuthenticatedUser actor, ListPagination pagination)
    implements Query<PagedResult<TenantResponseDto>> {}
