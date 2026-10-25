package com.vishop.iam.modules.identity.application.query;

import com.vishop.iam.shared.application.dto.AuthenticatedUser;
import io.github.coreplatform.common.domain.cqrs.Query;

public record AuthenticateQuery(String credential, boolean bearer)
    implements Query<AuthenticatedUser> {}
