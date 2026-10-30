package io.github.coreplatform.common.security.jwt;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnProperty(name = "vishop.security.jwt.enabled", havingValue = "true", matchIfMissing = true)
public class JwtAutoConfiguration {

  // #region jwtTokenProvider
  @Bean
  @ConditionalOnMissingBean
  public JwtTokenProvider jwtTokenProvider(
      @Value("${vishop.security.jwt.secret:${JWT_SECRET:}}") String secret) {
    return new JwtTokenProvider(secret);
  }
  // #endregion
}
