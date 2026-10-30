package com.vishop.iam.infra.openapi;

import io.github.coreplatform.common.domain.constants.ApiPaths;
import io.github.coreplatform.common.security.constants.AuthCookies;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.HeaderParameter;
import io.swagger.v3.oas.models.security.*;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.*;

/** Chỉ mô tả contract HTTP; không quyết định việc xác thực/ủy quyền khi request chạy. */
@Configuration(proxyBeanMethods = false)
public class AuthenticationOpenApiConfiguration {
  @Bean
  OpenApiCustomizer authenticationContract() {
    return api -> {
      if (api.getComponents() == null) api.setComponents(new Components());
      api.getComponents().addSecuritySchemes("accessCookie", cookie(AuthCookies.ACCESS_TOKEN));
      api.getComponents().addSecuritySchemes("refreshCookie", cookie(AuthCookies.REFRESH_TOKEN));
      if (api.getPaths() == null) return;
      api.getPaths()
          .forEach(
              (path, item) ->
                  item.readOperationsMap()
                      .forEach(
                          (method, operation) -> {
                            if (path.startsWith("/internal/")) return;
                            if (AuthCookies.requiresRequestHeader(method.name()))
                              operation.addParametersItem(
                                  new HeaderParameter()
                                      .name(AuthCookies.REQUEST_HEADER)
                                      .required(true)
                                      .description(
                                          "Required for state-changing cookie-authenticated requests")
                                      .schema(
                                          new StringSchema()
                                              ._default(AuthCookies.REQUEST_HEADER_VALUE)));
                            if (java.util.Set.of(
                                    ApiPaths.V1 + "/auth/login",
                                    ApiPaths.V1 + "/auth/register",
                                    ApiPaths.V1 + "/auth/logout")
                                .contains(path)) return;
                            operation.addSecurityItem(
                                new SecurityRequirement()
                                    .addList(
                                        path.equals(ApiPaths.V1 + "/auth/refresh")
                                            ? "refreshCookie"
                                            : "accessCookie"));
                          }));
    };
  }

  private SecurityScheme cookie(String name) {
    return new SecurityScheme()
        .type(SecurityScheme.Type.APIKEY)
        .in(SecurityScheme.In.COOKIE)
        .name(name)
        .description("HttpOnly cookie issued by IAM; the browser sends it automatically");
  }
}
