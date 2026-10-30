package com.vishop.iam.modules.identity.application.query;

import com.vishop.iam.modules.identity.application.dto.IamTokenClaims;
import io.github.coreplatform.common.domain.cqrs.Query;

public record VerifyAccessTokenQuery(String credential, boolean bearer)
    implements Query<IamTokenClaims.AccessToken> {}
