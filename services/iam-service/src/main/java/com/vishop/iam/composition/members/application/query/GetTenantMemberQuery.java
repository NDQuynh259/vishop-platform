package com.vishop.iam.composition.members.application.query;

import com.vishop.iam.modules.access.application.dto.TenantMemberResponseDto;
import com.vishop.iam.shared.application.dto.AuthenticatedUser;
import io.github.coreplatform.common.domain.cqrs.Query;
import java.util.UUID;

public record GetTenantMemberQuery(AuthenticatedUser actor, UUID tenant, UUID user)
    implements Query<TenantMemberResponseDto> {}
