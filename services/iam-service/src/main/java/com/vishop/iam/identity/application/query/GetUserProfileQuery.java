package com.vishop.iam.identity.application.query;

import com.vishop.iam.identity.application.dto.UserView;
import com.vishop.iam.shared.application.dto.IamActor;
import io.github.coreplatform.common.domain.cqrs.Query;

public record GetUserProfileQuery(IamActor actor) implements Query<UserView> {}
