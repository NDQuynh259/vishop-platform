package com.vishop.iam.modules.identity.application.query;

import com.vishop.iam.modules.identity.application.dto.ResolvedIdentity;
import io.github.coreplatform.common.domain.cqrs.QueryHandler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Component
public class IntrospectQueryHandler implements QueryHandler<IntrospectQuery, ResolvedIdentity> {
  private final AuthenticateQueryHandler authenticate;

  public IntrospectQueryHandler(AuthenticateQueryHandler authenticate) {
    this.authenticate = authenticate;
  }

  @Override
  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public ResolvedIdentity handle(IntrospectQuery query) {
    var credential = query.credential();
    var bearer = query.bearer();

    var actor = authenticate.handle(new AuthenticateQuery(credential, bearer));
    return new ResolvedIdentity(
        actor.userId(),
        actor.tenantId(),
        actor.systemRole(),
        actor.roles(),
        actor.permissions(),
        credential);
  }
}
