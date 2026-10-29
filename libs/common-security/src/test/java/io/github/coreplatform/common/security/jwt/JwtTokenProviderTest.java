package io.github.coreplatform.common.security.jwt;

import static org.assertj.core.api.Assertions.*;

import java.time.Instant;
import java.util.Date;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class JwtTokenProviderTest {
  final String secret = "test-generic-jwt-secret-with-at-least-thirty-two-bytes";
  final JwtTokenProvider jwt = new JwtTokenProvider(secret);

  @Test
  void roundTripsCallerDefinedPayloadWithoutIdentityAssumptions() {
    var claims =
        Map.<String, Object>of(
            "iss",
            "custom-service",
            "sub",
            "not-a-uuid",
            "invoiceNumber",
            "INV-2026-10",
            "amount",
            150000,
            "exp",
            Date.from(Instant.now().plusSeconds(60)));
    String token = jwt.encode(claims);
    assertThat(jwt.verify(token))
        .containsEntry("invoiceNumber", "INV-2026-10")
        .containsEntry("sub", "not-a-uuid");
    assertThat(jwt.decode(token)).containsEntry("iss", "custom-service");
  }

  @Test
  void mandatoryClaimsAreDecidedByCaller() {
    var token = jwt.encode(Map.of("custom", true));
    assertThat(jwt.verify(token))
        .containsEntry("custom", true)
        .doesNotContainKeys("iss", "aud", "exp", "tenantId");
  }

  @Test
  void rejectsWrongSignatureExpiredNotYetValidAndMalformedTokens() {
    String valid = jwt.encode(Map.of("value", "test"));
    var other =
        new JwtTokenProvider("another-generic-signing-secret-with-at-least-thirty-two-bytes");
    assertThatThrownBy(() -> other.verify(valid)).isInstanceOf(RuntimeException.class);
    String expired = jwt.encode(Map.of("exp", Date.from(Instant.now().minusSeconds(5))));
    String future = jwt.encode(Map.of("nbf", Date.from(Instant.now().plusSeconds(60))));
    assertThatThrownBy(() -> jwt.verify(expired)).isInstanceOf(RuntimeException.class);
    assertThatThrownBy(() -> jwt.verify(future)).isInstanceOf(RuntimeException.class);
    assertThatThrownBy(() -> jwt.decode("invalid")).isInstanceOf(RuntimeException.class);
    assertThatThrownBy(() -> jwt.verify(null)).isInstanceOf(RuntimeException.class);
  }

  @Test
  void autoConfigurationOnlyConfiguresCryptographicKey() {
    new ApplicationContextRunner()
        .withUserConfiguration(JwtAutoConfiguration.class)
        .withPropertyValues("vishop.security.jwt.secret=" + secret)
        .run(
            context -> {
              var provider = context.getBean(JwtTokenProvider.class);
              assertThat(provider.verify(provider.encode(Map.of("custom", 42))))
                  .containsEntry("custom", 42);
            });
  }

  @Test
  void rejectsMissingKey() {
    assertThatThrownBy(() -> new JwtTokenProvider("")).isInstanceOf(IllegalArgumentException.class);
  }
}
