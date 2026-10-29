package com.vishop.iam.modules.tenant.application.query.member;

import com.vishop.iam.modules.tenant.application.dto.MemberView;
import com.vishop.iam.shared.application.dto.AuthenticatedUser;
import io.github.coreplatform.common.domain.cqrs.Query;
import java.util.List;
import java.util.UUID;

public record ListTenantMembersQuery(AuthenticatedUser actor, UUID tenant)
    implements Query<List<MemberView>> {}
