package com.vishop.iam.modules.access.application.query;

import com.vishop.iam.modules.access.application.dto.RoleResponseDto;
import com.vishop.iam.shared.application.dto.IamActor;
import io.github.coreplatform.common.domain.cqrs.Query;
import java.util.List;
import java.util.UUID;

public record ListTenantRolesQuery(IamActor actor, UUID tenant)
    implements Query<List<RoleResponseDto>> {}
