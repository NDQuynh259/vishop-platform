package com.vishop.iam.modules.identity.application.query;

import com.vishop.iam.modules.identity.application.dto.UserView;
import com.vishop.iam.shared.application.dto.IamActor;
import io.github.coreplatform.common.domain.cqrs.Query;
import java.util.List;

public record ListCmsUsersQuery(IamActor actor, int limit, int offset)
    implements Query<List<UserView>> {}
