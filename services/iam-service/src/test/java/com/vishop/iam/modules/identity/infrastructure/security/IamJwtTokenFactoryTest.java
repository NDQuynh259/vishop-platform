package com.vishop.iam.modules.identity.infrastructure.security;

import static org.assertj.core.api.Assertions.*;

import com.vishop.iam.shared.application.dto.AuthenticatedUser;
import io.github.coreplatform.common.security.jwt.JwtTokenProvider;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class IamJwtTokenFactoryTest {
  final JwtTokenProvider jwt =
      new JwtTokenProvider("test-iam-factory-secret-with-at-least-thirty-two-bytes");
  final IamJwtTokenFactory factory = new IamJwtTokenFactory(jwt, 123000);

  @Test
  void iamControlsAccessLifetimeAndCredentialVersion() {
    var actor = new AuthenticatedUser(UUID.randomUUID(), null, "USER", null, Set.of());
    var claims = jwt.verify(factory.access(actor, 7));
    assertThat(claims.getExpiration().getTime() - claims.getIssuedAt().getTime()).isEqualTo(123000);
    assertThat(claims).containsEntry("token_use", "access").containsEntry("tokenVersion", 7);
    assertThat(claims.getIssuer()).isEqualTo("iam-service");
    assertThat(claims.getAudience()).containsExactly("vishop-api");
  }

  @Test
  void iamRefreshKeepsAbsoluteDeadlineAndUsesDistinctPurpose() {
    Instant expiry =
        Instant.now().plusSeconds(600).truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
    var claims = jwt.verify(factory.refresh(UUID.randomUUID(), null, expiry, 9));
    assertThat(claims.getExpiration().toInstant()).isEqualTo(expiry);
    assertThat(claims.getAudience()).containsExactly("vishop-refresh");
    assertThat(claims)
        .containsEntry("token_use", "refresh")
        .containsEntry("tokenVersion", 9)
        .doesNotContainKeys("systemRole", "permissions");
  }
}
