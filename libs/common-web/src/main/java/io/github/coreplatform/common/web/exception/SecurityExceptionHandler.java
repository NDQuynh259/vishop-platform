package io.github.coreplatform.common.web.exception;

import io.github.coreplatform.common.web.filter.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Handles Spring Security AccessDeniedException and AuthenticationException,
 * formatting them consistently into RFC 9457 Problem Details.
 * Active only when Spring Security is present on the classpath.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
@ConditionalOnClass({AccessDeniedException.class, AuthenticationException.class})
public class SecurityExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(SecurityExceptionHandler.class);

  // #region handleAccessDenied
  @ExceptionHandler(AccessDeniedException.class)
  public ResponseEntity<ProblemDetail> handleAccessDenied(
      AccessDeniedException ex, HttpServletRequest request) {

    String requestId = resolveRequestId(request);
    log.warn("[{}] Access denied on {} {}: {}", requestId, request.getMethod(), request.getRequestURI(), ex.getMessage());

    ProblemDetail problem = ProblemDetail.forStatusAndDetail(
        HttpStatus.FORBIDDEN, "Access is denied.");
    problem.setType(URI.create("/errors/forbidden"));
    problem.setTitle("Forbidden");
    problem.setInstance(resolveInstance(request));
    problem.setProperty("code", "access_denied");
    problem.setProperty("timestamp", Instant.now().toString());
    problem.setProperty("requestId", requestId);

    return ResponseEntity.status(HttpStatus.FORBIDDEN).body(problem);
  }
  // #endregion

  // #region handleAuthentication
  @ExceptionHandler(AuthenticationException.class)
  public ResponseEntity<ProblemDetail> handleAuthentication(
      AuthenticationException ex, HttpServletRequest request) {

    String requestId = resolveRequestId(request);
    log.warn("[{}] Authentication required on {} {}: {}", requestId, request.getMethod(), request.getRequestURI(), ex.getMessage());

    ProblemDetail problem = ProblemDetail.forStatusAndDetail(
        HttpStatus.UNAUTHORIZED, "Authentication is required to access this resource.");
    problem.setType(URI.create("/errors/unauthorized"));
    problem.setTitle("Unauthorized");
    problem.setInstance(resolveInstance(request));
    problem.setProperty("code", "unauthorized");
    problem.setProperty("timestamp", Instant.now().toString());
    problem.setProperty("requestId", requestId);

    return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(problem);
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
