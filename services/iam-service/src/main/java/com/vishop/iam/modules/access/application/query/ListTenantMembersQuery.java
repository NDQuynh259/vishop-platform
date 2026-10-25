package com.vishop.iam.modules.access.application.query;

import com.vishop.iam.modules.access.application.dto.MemberView;
import com.vishop.iam.shared.application.dto.AuthenticatedUser;
import io.github.coreplatform.common.domain.cqrs.Query;
import java.util.List;
import java.util.UUID;

public record ListTenantMembersQuery(AuthenticatedUser actor, UUID tenant)
    implements Query<List<MemberView>> {}
