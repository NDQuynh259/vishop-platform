package com.vishop.iam.access.application.query;

import com.vishop.iam.access.application.dto.MyTenant;
import com.vishop.iam.shared.application.dto.IamActor;
import io.github.coreplatform.common.domain.cqrs.Query;
import java.util.List;

public record ListMyTenantsQuery(IamActor actor) implements Query<List<MyTenant>> {}
