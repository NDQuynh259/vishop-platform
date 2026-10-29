package com.vishop.iam.modules.tenant.application.query.tenant;

import com.vishop.iam.modules.tenant.application.dto.TenantResponseDto;
import com.vishop.iam.shared.application.dto.AuthenticatedUser;
import io.github.coreplatform.common.domain.cqrs.Query;
import java.util.List;

public record ListCmsTenantsQuery(AuthenticatedUser actor, int limit, int offset)
    implements Query<List<TenantResponseDto>> {}
