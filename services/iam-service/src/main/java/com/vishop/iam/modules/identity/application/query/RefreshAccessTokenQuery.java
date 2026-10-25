package com.vishop.iam.modules.identity.application.query;

import com.vishop.iam.modules.identity.application.dto.AuthResponse;
import io.github.coreplatform.common.domain.cqrs.Query;

public record RefreshAccessTokenQuery(String refreshToken) implements Query<AuthResponse> {}
