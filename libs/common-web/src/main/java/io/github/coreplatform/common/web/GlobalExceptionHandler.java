package io.github.coreplatform.common.web;

import io.github.coreplatform.common.domain.exception.BadRequestException;
import io.github.coreplatform.common.domain.exception.BusinessRuleViolationException;
import io.github.coreplatform.common.domain.exception.ConflictException;
import io.github.coreplatform.common.domain.exception.DomainException;
import io.github.coreplatform.common.domain.exception.EntityNotFoundException;
import io.github.coreplatform.common.domain.exception.ForbiddenException;
import io.github.coreplatform.common.domain.exception.UnauthorizedException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Global exception handler mapping domain, validation, and web exceptions
 * to RFC 9457 Problem Details for HTTP APIs.
 *
 * <p>Extensions: {@code code}, {@code requestId}, {@code timestamp}, {@code errors}, {@code instance}.
 */
@RestControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE)
public class GlobalExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  // ==========================================
  // 1. Validation Failures (HTTP 422)
  // ==========================================

  // #region handleValidation
  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ProblemDetail> handleValidation(
      MethodArgumentNotValidException ex, HttpServletRequest request) {

    ProblemDetail problem = buildProblem(
        HttpStatus.UNPROCESSABLE_ENTITY,
        "/errors/validation-failed",
        "Validation failed",
        "validation_failed",
        "One or more fields did not pass validation.",
        request);

    List<ValidationError> errors = ex.getBindingResult().getFieldErrors().stream()
        .map(fe -> new ValidationError(fe.getField(), fe.getDefaultMessage()))
        .toList();

    if (errors.isEmpty() && ex.getBindingResult().hasGlobalErrors()) {
      errors = ex.getBindingResult().getGlobalErrors().stream()
          .map(ge -> new ValidationError(ge.getObjectName(), ge.getDefaultMessage()))
          .toList();
    }

    problem.setProperty("errors", errors);
    log.warn("[{}] Validation failed on {} {}: {} field error(s)",
        problem.getProperties().get("requestId"), request.getMethod(), request.getRequestURI(), errors.size());

    return ResponseEntity.unprocessableEntity().body(problem);
  }
  // #endregion

  // #region handleConstraintViolation
  @ExceptionHandler(ConstraintViolationException.class)
  public ResponseEntity<ProblemDetail> handleConstraintViolation(
      ConstraintViolationException ex, HttpServletRequest request) {

    ProblemDetail problem = buildProblem(
        HttpStatus.UNPROCESSABLE_ENTITY,
        "/errors/validation-failed",
        "Validation failed",
        "validation_failed",
        "One or more parameters did not pass validation.",
        request);

    List<ValidationError> errors = ex.getConstraintViolations().stream()
        .map(cv -> {
          String field = cv.getPropertyPath() != null ? cv.getPropertyPath().toString() : "parameter";
          int lastDot = field.lastIndexOf('.');
          if (lastDot >= 0 && lastDot < field.length() - 1) {
            field = field.substring(lastDot + 1);
          }
          return new ValidationError(field, cv.getMessage());
        })
        .toList();

    problem.setProperty("errors", errors);
    log.warn("[{}] Constraint violation on {} {}: {} violation(s)",
        problem.getProperties().get("requestId"), request.getMethod(), request.getRequestURI(), errors.size());

    return ResponseEntity.unprocessableEntity().body(problem);
  }
  // #endregion

  // #region handleHandlerMethodValidation
  @ExceptionHandler(HandlerMethodValidationException.class)
  public ResponseEntity<ProblemDetail> handleHandlerMethodValidation(
      HandlerMethodValidationException ex, HttpServletRequest request) {

    ProblemDetail problem = buildProblem(
        HttpStatus.UNPROCESSABLE_ENTITY,
        "/errors/validation-failed",
        "Validation failed",
        "validation_failed",
        "One or more parameters did not pass validation.",
        request);

    List<ValidationError> errors = ex.getAllValidationResults().stream()
        .flatMap(result -> result.getResolvableErrors().stream().map(error -> {
          String field = result.getMethodParameter().getParameterName();
          if (error instanceof org.springframework.validation.FieldError fe) {
            field = fe.getField();
          }
          return new ValidationError(field != null ? field : "parameter", error.getDefaultMessage());
        }))
        .toList();

    problem.setProperty("errors", errors);
    log.warn("[{}] Method validation failed on {} {}: {} error(s)",
        problem.getProperties().get("requestId"), request.getMethod(), request.getRequestURI(), errors.size());

    return ResponseEntity.unprocessableEntity().body(problem);
  }
  // #endregion

  // ==========================================
  // 2. Domain & Business Exceptions
  // ==========================================

  // #region handleNotFound
  @ExceptionHandler(EntityNotFoundException.class)
  public ResponseEntity<ProblemDetail> handleNotFound(
      EntityNotFoundException ex, HttpServletRequest request) {

    ProblemDetail problem = buildProblem(
        HttpStatus.NOT_FOUND,
        "/errors/entity-not-found",
        "Entity not found",
        ex.getCode(),
        ex.getMessage(),
        request);

    if (ex.getEntityType() != null) {
      problem.setProperty("entityType", ex.getEntityType());
    }
    if (ex.getEntityId() != null) {
      problem.setProperty("entityId", ex.getEntityId().toString());
    }

    log.warn("[{}] Entity not found on {} {}: {}",
        problem.getProperties().get("requestId"), request.getMethod(), request.getRequestURI(), ex.getMessage());

    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(problem);
  }
  // #endregion

  // #region handleConflict
  @ExceptionHandler(ConflictException.class)
  public ResponseEntity<ProblemDetail> handleConflict(
      ConflictException ex, HttpServletRequest request) {

    ProblemDetail problem = buildProblem(
        HttpStatus.CONFLICT,
        "/errors/conflict",
        "Conflict",
        ex.getCode(),
        ex.getMessage(),
        request);

    log.warn("[{}] Conflict on {} {}: {}",
        problem.getProperties().get("requestId"), request.getMethod(), request.getRequestURI(), ex.getMessage());

    return ResponseEntity.status(HttpStatus.CONFLICT).body(problem);
  }
  // #endregion

  // #region handleBusinessRule
  @ExceptionHandler(BusinessRuleViolationException.class)
  public ResponseEntity<ProblemDetail> handleBusinessRule(
      BusinessRuleViolationException ex, HttpServletRequest request) {

    ProblemDetail problem = buildProblem(
        HttpStatus.UNPROCESSABLE_ENTITY,
        "/errors/business-rule-violation",
        "Business rule violation",
        ex.getCode(),
        ex.getMessage(),
        request);

    log.warn("[{}] Business rule violation on {} {}: {}",
        problem.getProperties().get("requestId"), request.getMethod(), request.getRequestURI(), ex.getMessage());

    return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(problem);
  }
  // #endregion

  // #region handleUnauthorized
  @ExceptionHandler(UnauthorizedException.class)
  public ResponseEntity<ProblemDetail> handleUnauthorized(
      UnauthorizedException ex, HttpServletRequest request) {

    ProblemDetail problem = buildProblem(
        HttpStatus.UNAUTHORIZED,
        "/errors/unauthorized",
        "Unauthorized",
        ex.getCode(),
        ex.getMessage(),
        request);

    log.warn("[{}] Unauthorized on {} {}: {}",
        problem.getProperties().get("requestId"), request.getMethod(), request.getRequestURI(), ex.getMessage());

    return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(problem);
  }
  // #endregion

  // #region handleForbidden
  @ExceptionHandler(ForbiddenException.class)
  public ResponseEntity<ProblemDetail> handleForbidden(
      ForbiddenException ex, HttpServletRequest request) {

    ProblemDetail problem = buildProblem(
        HttpStatus.FORBIDDEN,
        "/errors/forbidden",
        "Forbidden",
        ex.getCode(),
        ex.getMessage(),
        request);

    log.warn("[{}] Forbidden on {} {}: {}",
        problem.getProperties().get("requestId"), request.getMethod(), request.getRequestURI(), ex.getMessage());

    return ResponseEntity.status(HttpStatus.FORBIDDEN).body(problem);
  }
  // #endregion

  // #region handleBadRequestException
  @ExceptionHandler(BadRequestException.class)
  public ResponseEntity<ProblemDetail> handleBadRequestException(
      BadRequestException ex, HttpServletRequest request) {

    ProblemDetail problem = buildProblem(
        HttpStatus.BAD_REQUEST,
        "/errors/bad-request",
        "Bad Request",
        ex.getCode(),
        ex.getMessage(),
        request);

    log.warn("[{}] Bad request on {} {}: {}",
        problem.getProperties().get("requestId"), request.getMethod(), request.getRequestURI(), ex.getMessage());

    return ResponseEntity.badRequest().body(problem);
  }
  // #endregion

  // #region handleDomain
  @ExceptionHandler(DomainException.class)
  public ResponseEntity<ProblemDetail> handleDomain(
      DomainException ex, HttpServletRequest request) {

    ProblemDetail problem = buildProblem(
        HttpStatus.UNPROCESSABLE_ENTITY,
        "/errors/domain-violation",
        "Domain violation",
        ex.getCode(),
        ex.getMessage(),
        request);

    log.warn("[{}] Domain exception on {} {}: {}",
        problem.getProperties().get("requestId"), request.getMethod(), request.getRequestURI(), ex.getMessage());

    return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(problem);
  }
  // #endregion

  // ==========================================
  // 3. HTTP Client Errors (400, 404, 405, 415)
  // ==========================================

  // #region handleIllegalArgumentOrState
  @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
  public ResponseEntity<ProblemDetail> handleIllegalArgumentOrState(
      RuntimeException ex, HttpServletRequest request) {

    ProblemDetail problem = buildProblem(
        HttpStatus.BAD_REQUEST,
        "/errors/bad-request",
        "Bad Request",
        "bad_request",
        ex.getMessage(),
        request);

    log.warn("[{}] Illegal argument/state on {} {}: {}",
        problem.getProperties().get("requestId"), request.getMethod(), request.getRequestURI(), ex.getMessage());

    return ResponseEntity.badRequest().body(problem);
  }
  // #endregion

  // #region handleMessageNotReadable
  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<ProblemDetail> handleMessageNotReadable(
      HttpMessageNotReadableException ex, HttpServletRequest request) {

    ProblemDetail problem = buildProblem(
        HttpStatus.BAD_REQUEST,
        "/errors/bad-request",
        "Malformed Request",
        "malformed_request",
        "Request body is malformed or could not be parsed.",
        request);

    log.warn("[{}] Malformed body on {} {}: {}",
        problem.getProperties().get("requestId"), request.getMethod(), request.getRequestURI(), ex.getMessage());

    return ResponseEntity.badRequest().body(problem);
  }
  // #endregion

  // #region handleTypeMismatch
  @ExceptionHandler(MethodArgumentTypeMismatchException.class)
  public ResponseEntity<ProblemDetail> handleTypeMismatch(
      MethodArgumentTypeMismatchException ex, HttpServletRequest request) {

    String detail = String.format("Parameter '%s' has an invalid value.", ex.getName());
    ProblemDetail problem = buildProblem(
        HttpStatus.BAD_REQUEST,
        "/errors/bad-request",
        "Type Mismatch",
        "type_mismatch",
        detail,
        request);

    log.warn("[{}] Type mismatch on {} {}: param '{}'",
        problem.getProperties().get("requestId"), request.getMethod(), request.getRequestURI(), ex.getName());

    return ResponseEntity.badRequest().body(problem);
  }
  // #endregion

  // #region handleMissingParam
  @ExceptionHandler(MissingServletRequestParameterException.class)
  public ResponseEntity<ProblemDetail> handleMissingParam(
      MissingServletRequestParameterException ex, HttpServletRequest request) {

    ProblemDetail problem = buildProblem(
        HttpStatus.BAD_REQUEST,
        "/errors/bad-request",
        "Missing Parameter",
        "missing_parameter",
        String.format("Required query parameter '%s' is missing.", ex.getParameterName()),
        request);

    log.warn("[{}] Missing query parameter on {} {}: '{}'",
        problem.getProperties().get("requestId"), request.getMethod(), request.getRequestURI(), ex.getParameterName());

    return ResponseEntity.badRequest().body(problem);
  }
  // #endregion

  // #region handleMissingHeader
  @ExceptionHandler(MissingRequestHeaderException.class)
  public ResponseEntity<ProblemDetail> handleMissingHeader(
      MissingRequestHeaderException ex, HttpServletRequest request) {

    ProblemDetail problem = buildProblem(
        HttpStatus.BAD_REQUEST,
        "/errors/bad-request",
        "Missing Header",
        "missing_header",
        String.format("Required header '%s' is missing.", ex.getHeaderName()),
        request);

    log.warn("[{}] Missing header on {} {}: '{}'",
        problem.getProperties().get("requestId"), request.getMethod(), request.getRequestURI(), ex.getHeaderName());

    return ResponseEntity.badRequest().body(problem);
  }
  // #endregion

  // #region handleMethodNotSupported
  @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
  public ResponseEntity<ProblemDetail> handleMethodNotSupported(
      HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {

    ProblemDetail problem = buildProblem(
        HttpStatus.METHOD_NOT_ALLOWED,
        "/errors/method-not-allowed",
        "Method Not Allowed",
        "method_not_allowed",
        ex.getMessage(),
        request);

    log.warn("[{}] Method not allowed on {} {}: {}",
        problem.getProperties().get("requestId"), request.getMethod(), request.getRequestURI(), ex.getMessage());

    return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(problem);
  }
  // #endregion

  // #region handleMediaTypeNotSupported
  @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
  public ResponseEntity<ProblemDetail> handleMediaTypeNotSupported(
      HttpMediaTypeNotSupportedException ex, HttpServletRequest request) {

    ProblemDetail problem = buildProblem(
        HttpStatus.UNSUPPORTED_MEDIA_TYPE,
        "/errors/unsupported-media-type",
        "Unsupported Media Type",
        "unsupported_media_type",
        ex.getMessage(),
        request);

    log.warn("[{}] Unsupported media type on {} {}: {}",
        problem.getProperties().get("requestId"), request.getMethod(), request.getRequestURI(), ex.getMessage());

    return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE).body(problem);
  }
  // #endregion

  // #region handleNoResourceFound
  @ExceptionHandler(NoResourceFoundException.class)
  public ResponseEntity<ProblemDetail> handleNoResourceFound(
      NoResourceFoundException ex, HttpServletRequest request) {

    ProblemDetail problem = buildProblem(
        HttpStatus.NOT_FOUND,
        "/errors/not-found",
        "Resource Not Found",
        "resource_not_found",
        ex.getMessage(),
        request);

    log.warn("[{}] Resource not found on {} {}: {}",
        problem.getProperties().get("requestId"), request.getMethod(), request.getRequestURI(), ex.getMessage());

    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(problem);
  }
  // #endregion

  // ==========================================
  // 4. Fallback Unexpected Error (HTTP 500)
  // ==========================================

  // #region handleGeneral
  @ExceptionHandler(Exception.class)
  public ResponseEntity<ProblemDetail> handleGeneral(Exception ex, HttpServletRequest request) {
    String requestId = resolveRequestId(request);

    // Never leak SQL queries, stack trace, or internal exception types to clients
    log.error("[{}] Unhandled server exception on {} {}: {}",
        requestId, request.getMethod(), request.getRequestURI(), ex.getMessage(), ex);

    ProblemDetail problem = buildProblem(
        HttpStatus.INTERNAL_SERVER_ERROR,
        "/errors/internal-server-error",
        "Internal Server Error",
        "internal_server_error",
        "An unexpected error occurred. Please contact support with the request ID.",
        request);

    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(problem);
  }
  // #endregion

  // ==========================================
  // Helpers
  // ==========================================

  // #region buildProblem
  private ProblemDetail buildProblem(
      HttpStatus status,
      String type,
      String title,
      String code,
      String detail,
      HttpServletRequest request) {

    ProblemDetail problem = ProblemDetail.forStatusAndDetail(
        status, detail != null ? detail : status.getReasonPhrase());
    problem.setType(URI.create(type));
    problem.setTitle(title);
    problem.setInstance(resolveInstance(request));
    problem.setProperty("code", code);
    problem.setProperty("timestamp", Instant.now().toString());
    problem.setProperty("requestId", resolveRequestId(request));
    return problem;
  }
  // #endregion

  // #region resolveInstance
  private URI resolveInstance(HttpServletRequest request) {
    if (request == null || request.getRequestURI() == null) {
      return URI.create("/");
    }
    return URI.create(request.getRequestURI());
  }
  // #endregion

  // #region resolveRequestId
  private String resolveRequestId(HttpServletRequest request) {
    if (request != null) {
      String header = request.getHeader(RequestIdFilter.HEADER);
      if (header != null && !header.isBlank()) {
        return header;
      }
      Object attr = request.getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE);
      if (attr != null && !attr.toString().isBlank()) {
        return attr.toString();
      }
    }
    String mdc = MDC.get("requestId");
    if (mdc != null && !mdc.isBlank()) {
      return mdc;
    }
    return UUID.randomUUID().toString();
  }
  // #endregion
}
