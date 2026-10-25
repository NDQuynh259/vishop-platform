package com.vishop.iam.modules.identity.application.query;

import com.vishop.iam.modules.identity.application.dto.UserView;
import com.vishop.iam.shared.application.dto.AuthenticatedUser;
import io.github.coreplatform.common.domain.cqrs.Query;

public record GetUserProfileQuery(AuthenticatedUser actor) implements Query<UserView> {}
