package io.github.coreplatform.common.security.jwt;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnProperty(
    name = "vishop.security.jwt.enabled",
    havingValue = "true",
    matchIfMissing = true)
public class JwtAutoConfiguration {

  // #region jwtTokenProvider
  @Bean
  @ConditionalOnMissingBean
  @ConditionalOnProperty(name = "vishop.security.jwt.secret")
  public JwtTokenProvider jwtTokenProvider(@Value("${vishop.security.jwt.secret:}") String secret) {
    return new JwtTokenProvider(secret);
  }

  // #endregion

  @Bean
  @ConditionalOnMissingBean
  @ConditionalOnProperty(
      name = {
        "vishop.security.jwt.access-token-secret",
        "vishop.security.jwt.refresh-token-secret"
      })
  public JwtTokenService jwtTokenService(
      @Value("${vishop.security.jwt.access-token-secret}") String accessSecret,
      @Value("${vishop.security.jwt.refresh-token-secret}") String refreshSecret) {
    return new JwtTokenService(accessSecret, refreshSecret);
  }
}
