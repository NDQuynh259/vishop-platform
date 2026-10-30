package com.vishop.iam.modules.identity.presentation.rest;

import com.vishop.iam.modules.identity.presentation.rest.dto.LoginRequest;
import com.vishop.iam.modules.identity.presentation.rest.dto.RegisterUserRequest;
import io.github.coreplatform.common.domain.constants.ApiPaths;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Endpoint skeletons retained while authentication is rebuilt. No credentials are issued. */
@RestController
@RequestMapping(ApiPaths.V1 + "/auth")
public class AuthController {
  @PostMapping("/register")
  public ResponseEntity<ProblemDetail> register(@Valid @RequestBody RegisterUserRequest request) {
    return notImplemented();
  }

  @PostMapping("/login")
  public ResponseEntity<ProblemDetail> login(@Valid @RequestBody LoginRequest request) {
    return notImplemented();
  }

  @PostMapping("/refresh")
  public ResponseEntity<ProblemDetail> refresh() {
    return notImplemented();
  }

  @PostMapping("/logout")
  public ResponseEntity<ProblemDetail> logout() {
    return notImplemented();
  }

  private ResponseEntity<ProblemDetail> notImplemented() {
    var problem =
        ProblemDetail.forStatusAndDetail(
            HttpStatus.NOT_IMPLEMENTED,
            "Authentication is being rebuilt. This endpoint is not implemented yet.");
    problem.setProperty("code", "auth_not_implemented");
    return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED)
        .cacheControl(CacheControl.noStore())
        .body(problem);
  }
}
