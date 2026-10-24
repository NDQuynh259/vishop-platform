package com.vishop.iam.modules.identity.application.query;

import com.vishop.iam.modules.identity.application.dto.AuthResponse;
import com.vishop.iam.shared.application.dto.IamActor;
import io.github.coreplatform.common.domain.cqrs.Query;

public record RefreshAccessTokenQuery(IamActor actor) implements Query<AuthResponse> {}
