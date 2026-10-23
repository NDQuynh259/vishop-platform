package io.github.coreplatform.common.web.openapi;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/** Auto-configuration for OpenAPI / Swagger documentation. */
@AutoConfiguration
@ConditionalOnClass(OpenAPI.class)
public class OpenApiAutoConfiguration {

  // #region defaultOpenAPI
  @Bean
  @ConditionalOnMissingBean
  public OpenAPI defaultOpenAPI() {
    return new OpenAPI()
        .info(
            new Info()
                .title("ViShop Platform REST API")
                .description("Multi-tenant E-Commerce Microservices API Specification")
                .version("v1.0.0"));
  }
  // #endregion
}
