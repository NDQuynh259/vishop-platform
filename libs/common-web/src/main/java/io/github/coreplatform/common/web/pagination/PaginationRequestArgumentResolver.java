package io.github.coreplatform.common.web.pagination;

import java.util.Locale;
import org.springframework.core.MethodParameter;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
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
      WebDataBinderFactory binderFactory)
      throws Exception {
    var pagination =
        new PaginationRequest(
            request.getParameter("cursor"),
            integerParameter(request, "page", parameter),
            integerParameter(request, "limit", parameter),
            pageMode(request, parameter));
    var binder = binderFactory.createBinder(request, pagination, "pagination");
    binder.validate();
    if (binder.getBindingResult().hasErrors())
      throw new MethodArgumentNotValidException(parameter, binder.getBindingResult());
    return pagination;
  }

  private PageMode pageMode(NativeWebRequest request, MethodParameter parameter)
      throws MethodArgumentNotValidException {
    String value = request.getParameter("pageMode");
    if (value == null) value = request.getParameter("paginationMode");
    if (value == null) return null;
    try {
      return PageMode.valueOf(value.toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException failure) {
      var errors = new BeanPropertyBindingResult(null, "pagination");
      errors.addError(
          new FieldError(
              "pagination",
              "pageMode",
              value,
              false,
              null,
              null,
              "pageMode must be offset or cursor"));
      throw new MethodArgumentNotValidException(parameter, errors);
    }
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
