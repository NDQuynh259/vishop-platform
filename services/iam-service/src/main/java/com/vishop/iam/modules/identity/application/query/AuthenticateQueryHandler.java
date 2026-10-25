package com.vishop.iam.modules.identity.application.query;

import com.vishop.iam.modules.identity.application.port.AccessTokenPort;
import com.vishop.iam.shared.application.dto.IamActor;
import io.github.coreplatform.common.domain.cqrs.QueryHandler;
import io.github.coreplatform.common.domain.exception.UnauthorizedException;
import org.springframework.stereotype.Component;

@Component
public class AuthenticateQueryHandler implements QueryHandler<AuthenticateQuery, IamActor> {
  private final AccessTokenPort tokens;

  public AuthenticateQueryHandler(AccessTokenPort tokens) {
    this.tokens = tokens;
  }

  public IamActor handle(AuthenticateQuery query) {
    if (!query.bearer()) throw new UnauthorizedException("Bearer access token is required");
    return tokens.verify(query.credential());
  }
}
