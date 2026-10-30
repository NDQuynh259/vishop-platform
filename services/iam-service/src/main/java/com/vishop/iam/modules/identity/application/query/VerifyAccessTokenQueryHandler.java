package com.vishop.iam.modules.identity.application.query;

import com.vishop.iam.modules.identity.application.dto.IamTokenClaims;
import com.vishop.iam.modules.identity.application.port.TokenPort;
import io.github.coreplatform.common.domain.cqrs.QueryHandler;
import io.github.coreplatform.common.domain.exception.UnauthorizedException;
import org.springframework.stereotype.Component;

@Component
public class VerifyAccessTokenQueryHandler
    implements QueryHandler<VerifyAccessTokenQuery, IamTokenClaims.AccessToken> {
  private final TokenPort tokens;

  public VerifyAccessTokenQueryHandler(TokenPort tokens) {
    this.tokens = tokens;
  }

  public IamTokenClaims.AccessToken handle(VerifyAccessTokenQuery query) {
    if (!query.bearer()) throw new UnauthorizedException("Bearer access token is required");
    return tokens.verifyAccessToken(query.credential());
  }
}
