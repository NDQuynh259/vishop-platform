package io.github.coreplatform.common.security.jwt;

import static org.assertj.core.api.Assertions.*;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class JwtTokenServiceTest {
  private static final String ACCESS = "test-access-signing-secret-at-least-thirty-two-bytes";
  private static final String REFRESH = "test-refresh-signing-secret-at-least-thirty-two-bytes";
  private final Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
  private final JwtTokenService tokens =
      new JwtTokenService(ACCESS, REFRESH, Clock.fixed(now, ZoneOffset.UTC));

  @Test
  void generatesAccessAndRefreshWithCallerClaimsAndIndependentLifetimes() {
    String access =
        tokens.generateAccessToken(
            Map.of("sub", "caller-defined-id", "custom", "access"), Duration.ofMinutes(15));
    String refresh =
        tokens.generateRefreshToken(
            Map.of("sub", "caller-defined-id", "custom", "refresh"), Duration.ofDays(7));
    var accessClaims = tokens.verifyAccessToken(access);
    var refreshClaims = tokens.verifyRefreshToken(refresh);
    assertThat(accessClaims)
        .containsEntry("custom", "access")
        .doesNotContainKeys("iss", "aud", "tenantId", "roles", "permissions", "token_use");
    assertThat(refreshClaims).containsEntry("custom", "refresh");
    assertThat(accessClaims.getIssuedAt().toInstant()).isEqualTo(now);
    assertThat(accessClaims.getExpiration().toInstant()).isEqualTo(now.plusSeconds(900));
    assertThat(refreshClaims.getExpiration().toInstant()).isEqualTo(now.plus(Duration.ofDays(7)));
    assertThat(accessClaims.getId()).isNotBlank().isNotEqualTo(refreshClaims.getId());
  }

  @Test
  void absoluteDeadlineDoesNotSlideWhenRefreshIsReissuedLater() {
    Instant deadline = now.plusSeconds(300);
    String first = tokens.generateRefreshToken(Map.of("sid", "same-session"), deadline);
    var later =
        new JwtTokenService(ACCESS, REFRESH, Clock.fixed(now.plusSeconds(10), ZoneOffset.UTC));
    String second = later.generateRefreshToken(Map.of("sid", "same-session"), deadline);
    assertThat(tokens.verifyRefreshToken(first).getExpiration())
        .isEqualTo(tokens.verifyRefreshToken(second).getExpiration());
    assertThat(tokens.verifyRefreshToken(second).getIssuedAt().toInstant())
        .isEqualTo(now.plusSeconds(10));
  }

  @Test
  void signingKeysCannotBeUsedInterchangeably() {
    String access =
        tokens.generateAccessToken(Map.of("token_use", "refresh"), Duration.ofMinutes(1));
    String refresh =
        tokens.generateRefreshToken(Map.of("token_use", "access"), Duration.ofMinutes(1));
    assertThatThrownBy(() -> tokens.verifyAccessToken(refresh))
        .isInstanceOf(io.jsonwebtoken.security.SignatureException.class);
    assertThatThrownBy(() -> tokens.verifyRefreshToken(access))
        .isInstanceOf(io.jsonwebtoken.security.SignatureException.class);
  }

  @Test
  void generationSetsTimesWithoutMutatingInputAndPreservesExplicitJwtId() {
    var claims = new HashMap<String, Object>();
    claims.put("jti", "caller-jwt-id");
    claims.put("iat", Date.from(now.minusSeconds(600)));
    claims.put("exp", Date.from(now.plusSeconds(999999)));
    var original = new HashMap<>(claims);
    var result =
        tokens.verifyAccessToken(tokens.generateAccessToken(claims, Duration.ofSeconds(60)));
    assertThat(claims).isEqualTo(original);
    assertThat(result.getId()).isEqualTo("caller-jwt-id");
    assertThat(result.getIssuedAt().toInstant()).isEqualTo(now);
    assertThat(result.getExpiration().toInstant()).isEqualTo(now.plusSeconds(60));
  }

  @Test
  void tokensExpireAndVerificationRequiresTimeMetadata() {
    var past =
        new JwtTokenService(ACCESS, REFRESH, Clock.fixed(now.minusSeconds(30), ZoneOffset.UTC));
    assertThatThrownBy(
            () ->
                tokens.verifyAccessToken(past.generateAccessToken(Map.of(), Duration.ofSeconds(1))))
        .isInstanceOf(io.jsonwebtoken.ExpiredJwtException.class);
    assertThatThrownBy(
            () ->
                tokens.verifyRefreshToken(
                    past.generateRefreshToken(Map.of(), Duration.ofSeconds(1))))
        .isInstanceOf(io.jsonwebtoken.ExpiredJwtException.class);
    String missingTimes = new JwtTokenProvider(ACCESS).encode(Map.of("custom", true));
    assertThatThrownBy(() -> tokens.verifyAccessToken(missingTimes))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejectsWeakIdenticalKeysAndInvalidLifetimes() {
    assertThatThrownBy(() -> new JwtTokenService(ACCESS, ACCESS))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new JwtTokenService("short", REFRESH))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new JwtTokenService(ACCESS, "short"))
        .isInstanceOf(IllegalArgumentException.class);
    for (Duration duration :
        Arrays.asList(null, Duration.ZERO, Duration.ofMillis(999), Duration.ofSeconds(-1))) {
      assertThatThrownBy(() -> tokens.generateAccessToken(Map.of(), duration))
          .isInstanceOf(IllegalArgumentException.class);
      assertThatThrownBy(() -> tokens.generateRefreshToken(Map.of(), duration))
          .isInstanceOf(IllegalArgumentException.class);
    }
  }

  @Test
  void autoConfigurationCreatesDualKeyServiceWithoutRequiringLegacySecret() {
    context()
        .withPropertyValues(
            "vishop.security.jwt.access-token-secret=" + ACCESS,
            "vishop.security.jwt.refresh-token-secret=" + REFRESH)
        .run(
            application -> {
              assertThat(application)
                  .hasSingleBean(JwtTokenService.class)
                  .doesNotHaveBean(JwtTokenProvider.class);
              var service = application.getBean(JwtTokenService.class);
              assertThat(
                      service.verifyAccessToken(
                          service.generateAccessToken(
                              Map.of("custom", true), Duration.ofMinutes(1))))
                  .containsEntry("custom", true);
            });
  }

  @Test
  void absentOrDisabledConfigurationDoesNotCreateTokenBeans() {
    context()
        .run(
            application ->
                assertThat(application)
                    .doesNotHaveBean(JwtTokenService.class)
                    .doesNotHaveBean(JwtTokenProvider.class));
    context()
        .withPropertyValues(
            "vishop.security.jwt.enabled=false",
            "vishop.security.jwt.access-token-secret=" + ACCESS,
            "vishop.security.jwt.refresh-token-secret=" + REFRESH)
        .run(application -> assertThat(application).doesNotHaveBean(JwtTokenService.class));
    context()
        .withPropertyValues("vishop.security.jwt.access-token-secret=" + ACCESS)
        .run(application -> assertThat(application).doesNotHaveBean(JwtTokenService.class));
  }

  private ApplicationContextRunner context() {
    return new ApplicationContextRunner().withUserConfiguration(JwtAutoConfiguration.class);
  }
}
