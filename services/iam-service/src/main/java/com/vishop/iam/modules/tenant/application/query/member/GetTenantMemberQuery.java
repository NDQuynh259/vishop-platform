package com.vishop.iam.modules.tenant.application.query.member;

import com.vishop.iam.modules.tenant.application.dto.TenantMemberResponseDto;
import com.vishop.iam.shared.application.dto.AuthenticatedUser;
import io.github.coreplatform.common.domain.cqrs.Query;
import java.util.UUID;

public record GetTenantMemberQuery(AuthenticatedUser actor, UUID tenant, UUID user)
    implements Query<TenantMemberResponseDto> {}
