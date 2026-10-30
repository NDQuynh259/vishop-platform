package com.vishop.iam.infra.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.coreplatform.common.domain.constants.ApiPaths;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Temporary HTTP gate: business endpoints stay unavailable until authentication is rebuilt. */
@Configuration(proxyBeanMethods = false)
public class IamApiResetConfiguration implements WebMvcConfigurer {
  private static final Set<String> AUTH_PATHS =
      Set.of(
          ApiPaths.V1 + "/auth/login", ApiPaths.V1 + "/auth/register",
          ApiPaths.V1 + "/auth/refresh", ApiPaths.V1 + "/auth/logout");
  private final ObjectMapper json;

  public IamApiResetConfiguration(ObjectMapper json) {
    this.json = json;
  }

  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    registry.addInterceptor(
        new HandlerInterceptor() {
          @Override
          public boolean preHandle(
              HttpServletRequest request, HttpServletResponse response, Object handler)
              throws IOException {
            if (!(handler instanceof HandlerMethod method)
                || !method.getBeanType().getPackageName().startsWith("com.vishop.iam.modules.")
                || ("POST".equals(request.getMethod())
                    && AUTH_PATHS.contains(request.getRequestURI()))) return true;
            var problem =
                ProblemDetail.forStatusAndDetail(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "IAM business APIs are unavailable while authentication is being rebuilt.");
            problem.setProperty("code", "iam_authentication_reset");
            response.setStatus(HttpStatus.SERVICE_UNAVAILABLE.value());
            response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
            response.setHeader("Cache-Control", "no-store");
            json.writeValue(response.getOutputStream(), problem);
            return false;
          }
        });
  }
}
