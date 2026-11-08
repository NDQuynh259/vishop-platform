package com.vishop.iam.modules.tenant.application.query.role;

import com.vishop.iam.modules.tenant.application.dto.RoleResponseDto;
import com.vishop.iam.shared.application.dto.AuthenticatedUser;
import io.github.coreplatform.common.domain.cqrs.Query;
import io.github.coreplatform.common.domain.pagination.ListPagination;
import io.github.coreplatform.common.domain.pagination.PagedResult;
import java.util.UUID;

public record ListTenantRolesQuery(AuthenticatedUser actor, UUID tenant, ListPagination pagination)
    implements Query<PagedResult<RoleResponseDto>> {}
