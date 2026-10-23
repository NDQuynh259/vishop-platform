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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Handles database constraint violations and optimistic locking conflicts, formatting them
 * consistently into RFC 9457 Problem Details without leaking SQL internals. Active only when Spring
 * DAO / Data access classes are on the classpath.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
@ConditionalOnClass({
  DataIntegrityViolationException.class,
  OptimisticLockingFailureException.class
})
public class DatabaseExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(DatabaseExceptionHandler.class);

  // #region handleDataIntegrityViolation
  @ExceptionHandler(DataIntegrityViolationException.class)
  public ResponseEntity<ProblemDetail> handleDataIntegrityViolation(
      DataIntegrityViolationException ex, HttpServletRequest request) {

    String requestId = resolveRequestId(request);
    log.error(
        "[{}] Database integrity constraint violation on {} {}: {}",
        requestId,
        request.getMethod(),
        request.getRequestURI(),
        ex.getMessage());

    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(
            HttpStatus.CONFLICT, "A database conflict or unique constraint violation occurred.");
    problem.setType(URI.create("/errors/conflict"));
    problem.setTitle("Conflict");
    problem.setInstance(resolveInstance(request));
    problem.setProperty("code", "database_conflict");
    problem.setProperty("timestamp", Instant.now().toString());
    problem.setProperty("requestId", requestId);

    return ResponseEntity.status(HttpStatus.CONFLICT).body(problem);
  }

  // #endregion

  // #region handleOptimisticLockingFailure
  @ExceptionHandler(OptimisticLockingFailureException.class)
  public ResponseEntity<ProblemDetail> handleOptimisticLockingFailure(
      OptimisticLockingFailureException ex, HttpServletRequest request) {

    String requestId = resolveRequestId(request);
    log.warn(
        "[{}] Optimistic lock collision on {} {}: {}",
        requestId,
        request.getMethod(),
        request.getRequestURI(),
        ex.getMessage());

    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(
            HttpStatus.CONFLICT, "The requested resource was modified concurrently. Please retry.");
    problem.setType(URI.create("/errors/conflict"));
    problem.setTitle("Conflict");
    problem.setInstance(resolveInstance(request));
    problem.setProperty("code", "optimistic_lock_conflict");
    problem.setProperty("timestamp", Instant.now().toString());
    problem.setProperty("requestId", requestId);

    return ResponseEntity.status(HttpStatus.CONFLICT).body(problem);
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
