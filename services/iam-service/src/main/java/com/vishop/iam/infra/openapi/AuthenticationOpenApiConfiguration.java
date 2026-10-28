package com.vishop.iam.infra.openapi;

import io.github.coreplatform.common.domain.constants.ApiPaths;
import io.github.coreplatform.common.security.constants.AuthCookies;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.HeaderParameter;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class AuthenticationOpenApiConfiguration {
  @Bean
  OpenApiCustomizer cookieAuthenticationContract() {
    return api -> {
      if (api.getComponents() == null) api.setComponents(new Components());
      api.getComponents()
          .addSecuritySchemes(
              "accessCookie",
              new SecurityScheme()
                  .type(SecurityScheme.Type.APIKEY)
                  .in(SecurityScheme.In.COOKIE)
                  .name(AuthCookies.ACCESS_TOKEN)
                  .description(
                      "HttpOnly cookie set automatically by IAM; never copy a JWT from a JSON response."));
      api.getComponents()
          .addSecuritySchemes(
              "refreshCookie",
              new SecurityScheme()
                  .type(SecurityScheme.Type.APIKEY)
                  .in(SecurityScheme.In.COOKIE)
                  .name(AuthCookies.REFRESH_TOKEN));
      if (api.getPaths() == null) return;
      api.getPaths()
          .forEach(
              (path, item) ->
                  item.readOperationsMap()
                      .forEach(
                          (method, operation) -> {
                            if (path.startsWith("/internal/")) return;
                            if (AuthCookies.requiresRequestHeader(method.name())) {
                              operation.addParametersItem(
                                  new HeaderParameter()
                                      .name(AuthCookies.REQUEST_HEADER)
                                      .required(true)
                                      .description(
                                          "Required non-simple header for cookie-authenticated API writes.")
                                      .schema(
                                          new StringSchema()
                                              ._default(AuthCookies.REQUEST_HEADER_VALUE)));
                            }
                            if (path.equals(ApiPaths.V1 + "/auth/register")
                                || path.equals(ApiPaths.V1 + "/auth/login")
                                || path.equals(ApiPaths.V1 + "/auth/logout")) return;
                            var requirement = new SecurityRequirement();
                            if (!path.equals(ApiPaths.V1 + "/auth/refresh"))
                              requirement.addList("accessCookie");
                            if (path.equals(ApiPaths.V1 + "/auth/refresh")
                                || path.equals(ApiPaths.V1 + "/auth/switch-tenant"))
                              requirement.addList("refreshCookie");
                            operation.addSecurityItem(requirement);
                          }));
    };
  }
}
