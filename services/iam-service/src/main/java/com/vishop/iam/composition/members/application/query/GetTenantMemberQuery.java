package com.vishop.iam.composition.members.application.query;

import com.vishop.iam.contracts.access.TenantMemberResponseDto;
import com.vishop.iam.shared.application.dto.IamActor;
import io.github.coreplatform.common.domain.cqrs.Query;
import java.util.UUID;

public record GetTenantMemberQuery(IamActor actor, UUID tenant, UUID user)
    implements Query<TenantMemberResponseDto> {}
