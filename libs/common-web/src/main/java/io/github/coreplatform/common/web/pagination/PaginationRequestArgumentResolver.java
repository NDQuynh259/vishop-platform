package io.github.coreplatform.common.web.pagination;

import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/** Binds shared pagination parameters without relying on constructor selection by model binding. */
public final class PaginationRequestArgumentResolver implements HandlerMethodArgumentResolver {
  @Override
  public boolean supportsParameter(MethodParameter parameter) {
    return parameter.getParameterType() == PaginationRequest.class;
  }

  @Override
  public PaginationRequest resolveArgument(
      MethodParameter parameter,
      ModelAndViewContainer container,
      NativeWebRequest request,
      WebDataBinderFactory binderFactory) {
    return new PaginationRequest(
        request.getParameter("cursor"),
        integerParameter(request, "page", parameter),
        integerParameter(request, "limit", parameter));
  }

  private Integer integerParameter(
      NativeWebRequest request, String name, MethodParameter parameter) {
    String value = request.getParameter(name);
    if (value == null || value.isBlank()) return null;
    try {
      return Integer.valueOf(value.trim());
    } catch (NumberFormatException failure) {
      throw new MethodArgumentTypeMismatchException(value, Integer.class, name, parameter, failure);
    }
  }
}
