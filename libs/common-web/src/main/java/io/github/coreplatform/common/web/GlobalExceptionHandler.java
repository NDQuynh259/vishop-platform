package io.github.coreplatform.common.web;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

/**
 * Global exception handler mapping domain and validation exceptions
 * to RFC 9457 Problem Details for HTTP APIs.
 *
 * <p>Extensions: {@code code}, {@code requestId}, {@code timestamp}, {@code errors}.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleValidation(
            MethodArgumentNotValidException ex, WebRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "One or more fields did not pass validation.");
        problem.setType(URI.create("/errors/validation-failed"));
        problem.setTitle("Validation failed");
        problem.setProperty("code", "validation_failed");
        problem.setProperty("timestamp", Instant.now().toString());
        problem.setProperty("requestId", getRequestId(request));

        List<FieldError> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> new FieldError(fe.getField(), fe.getDefaultMessage()))
                .toList();
        problem.setProperty("errors", errors);

        return ResponseEntity.unprocessableEntity().body(problem);
    }

    @ExceptionHandler(io.github.coreplatform.common.domain.exception.EntityNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleNotFound(
            io.github.coreplatform.common.domain.exception.EntityNotFoundException ex, WebRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problem.setType(URI.create("/errors/entity-not-found"));
        problem.setTitle("Entity not found");
        problem.setProperty("code", ex.getCode());
        problem.setProperty("timestamp", Instant.now().toString());
        problem.setProperty("requestId", getRequestId(request));
        problem.setProperty("entityType", ex.getEntityType());
        problem.setProperty("entityId", ex.getEntityId());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(problem);
    }

    @ExceptionHandler(io.github.coreplatform.common.domain.exception.ConflictException.class)
    public ResponseEntity<ProblemDetail> handleConflict(
            io.github.coreplatform.common.domain.exception.ConflictException ex, WebRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problem.setType(URI.create("/errors/conflict"));
        problem.setTitle("Conflict");
        problem.setProperty("code", ex.getCode());
        problem.setProperty("timestamp", Instant.now().toString());
        problem.setProperty("requestId", getRequestId(request));
        return ResponseEntity.status(HttpStatus.CONFLICT).body(problem);
    }

    @ExceptionHandler(io.github.coreplatform.common.domain.exception.BusinessRuleViolationException.class)
    public ResponseEntity<ProblemDetail> handleBusinessRule(
            io.github.coreplatform.common.domain.exception.BusinessRuleViolationException ex, WebRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
        problem.setType(URI.create("/errors/business-rule-violation"));
        problem.setTitle("Business rule violation");
        problem.setProperty("code", ex.getCode());
        problem.setProperty("timestamp", Instant.now().toString());
        problem.setProperty("requestId", getRequestId(request));
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(problem);
    }

    @ExceptionHandler(io.github.coreplatform.common.domain.exception.DomainException.class)
    public ResponseEntity<ProblemDetail> handleDomain(
            io.github.coreplatform.common.domain.exception.DomainException ex, WebRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problem.setType(URI.create("/errors/domain-error"));
        problem.setTitle("Domain error");
        problem.setProperty("code", ex.getCode());
        problem.setProperty("timestamp", Instant.now().toString());
        problem.setProperty("requestId", getRequestId(request));
        return ResponseEntity.badRequest().body(problem);
    }

    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    public ResponseEntity<ProblemDetail> handleIllegal(RuntimeException ex, WebRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problem.setType(URI.create("/errors/bad-request"));
        problem.setTitle("Bad Request");
        problem.setProperty("code", "bad_request");
        problem.setProperty("timestamp", Instant.now().toString());
        problem.setProperty("requestId", getRequestId(request));
        return ResponseEntity.badRequest().body(problem);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleGeneral(Exception ex, WebRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "An unexpected error occurred.");
        problem.setType(URI.create("/errors/internal-server-error"));
        problem.setTitle("Internal Server Error");
        problem.setProperty("code", "internal_server_error");
        problem.setProperty("timestamp", Instant.now().toString());
        problem.setProperty("requestId", getRequestId(request));
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(problem);
    }

    private String getRequestId(WebRequest request) {
        Object attr = request.getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE, 0);
        return attr != null ? attr.toString() : UUID.randomUUID().toString();
    }

    private record FieldError(String field, String message) {}
}
