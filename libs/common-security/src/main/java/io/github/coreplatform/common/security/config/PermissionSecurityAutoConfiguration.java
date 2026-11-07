package io.github.coreplatform.common.security.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.security.authorization.method.PrePostTemplateDefaults;

/** Enables permission annotation templates for services using {@code @EnableMethodSecurity}. */
@AutoConfiguration
public class PermissionSecurityAutoConfiguration {
  @Bean
  @ConditionalOnMissingBean(PrePostTemplateDefaults.class)
  static PrePostTemplateDefaults permissionTemplateDefaults() {
    return new PrePostTemplateDefaults();
  }
}
