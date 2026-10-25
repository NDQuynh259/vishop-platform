package com.vishop.iam.modules.identity.application.query;

import com.vishop.iam.modules.identity.application.dto.AuthResponse;
import com.vishop.iam.modules.identity.application.service.TokenService;
import io.github.coreplatform.common.domain.cqrs.QueryHandler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class RefreshAccessTokenQueryHandler
    implements QueryHandler<RefreshAccessTokenQuery, AuthResponse> {
  private final TokenService tokens;

  public RefreshAccessTokenQueryHandler(TokenService tokens) {
    this.tokens = tokens;
  }

  @Transactional(
      readOnly = true,
      isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
  public AuthResponse handle(RefreshAccessTokenQuery query) {
    return tokens.refresh(query.refreshToken());
  }
}
