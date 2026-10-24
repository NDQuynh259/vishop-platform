package com.vishop.iam.modules.tenant.application.query;

import com.vishop.iam.contracts.tenant.TenantResponseDto;
import com.vishop.iam.shared.application.dto.IamActor;
import io.github.coreplatform.common.domain.cqrs.Query;
import java.util.List;

public record ListCmsTenantsQuery(IamActor actor, int limit, int offset)
    implements Query<List<TenantResponseDto>> {}
