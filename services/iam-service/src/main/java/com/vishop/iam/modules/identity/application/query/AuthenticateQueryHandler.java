package com.vishop.iam.modules.identity.application.query;

import com.vishop.iam.modules.identity.application.port.AccessTokenPort;
import com.vishop.iam.shared.application.dto.AuthenticatedUser;
import io.github.coreplatform.common.domain.cqrs.QueryHandler;
import io.github.coreplatform.common.domain.exception.UnauthorizedException;
import org.springframework.stereotype.Component;

@Component
public class AuthenticateQueryHandler
    implements QueryHandler<AuthenticateQuery, AuthenticatedUser> {
  private final AccessTokenPort tokens;

  public AuthenticateQueryHandler(AccessTokenPort tokens) {
    this.tokens = tokens;
  }

  public AuthenticatedUser handle(AuthenticateQuery query) {
    if (!query.bearer()) throw new UnauthorizedException("Bearer access token is required");
    return tokens.verify(query.credential());
  }
}
