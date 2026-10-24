package com.vishop.iam.modules.identity.application.query;

import com.vishop.iam.modules.identity.application.dto.ResolvedIdentity;
import io.github.coreplatform.common.domain.cqrs.Query;

public record IntrospectQuery(String credential, boolean bearer)
    implements Query<ResolvedIdentity> {}
