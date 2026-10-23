package com.vishop.iam.tenant.application.query;

import com.vishop.iam.shared.application.dto.IamActor;
import com.vishop.iam.tenant.application.dto.TenantResponseDto;
import io.github.coreplatform.common.domain.cqrs.Query;
import java.util.List;

public record ListCmsTenantsQuery(IamActor actor, int limit, int offset)
    implements Query<List<TenantResponseDto>> {}
