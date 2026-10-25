package com.vishop.iam.modules.identity.presentation.rest;

import java.net.URI;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Repeatable-read token issuance may need a client retry when account updates race. */
@RestControllerAdvice(basePackages = "com.vishop.iam.modules.identity")
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class IdentityConcurrencyExceptionHandler {
  @ExceptionHandler(ConcurrencyFailureException.class)
  public ResponseEntity<ProblemDetail> conflict(ConcurrencyFailureException failure) {
    var problem =
        ProblemDetail.forStatusAndDetail(
            HttpStatus.CONFLICT,
            "Security context changed concurrently. Please retry the request.");
    problem.setType(URI.create("/errors/security-context-conflict"));
    problem.setTitle("Conflict");
    problem.setProperty("code", "security_context_conflict");
    return ResponseEntity.status(HttpStatus.CONFLICT).body(problem);
  }
}
