package com.vishop.iam.modules.identity.presentation.rest;

import com.vishop.iam.infra.security.IamPrincipal;
import com.vishop.iam.modules.identity.application.command.LoginCommand;
import com.vishop.iam.modules.identity.application.command.LogoutCommand;
import com.vishop.iam.modules.identity.application.command.RegisterUserCommand;
import com.vishop.iam.modules.identity.application.command.SwitchTenantCommand;
import com.vishop.iam.modules.identity.application.dto.AuthResponse;
import com.vishop.iam.modules.identity.application.dto.AuthenticationResult;
import com.vishop.iam.modules.identity.application.dto.UserView;
import com.vishop.iam.modules.identity.application.query.GetUserProfileQuery;
import com.vishop.iam.modules.identity.application.query.RefreshAccessTokenQuery;
import com.vishop.iam.modules.identity.infrastructure.security.IamAuthenticationFilter;
import com.vishop.iam.modules.identity.presentation.rest.dto.LoginRequest;
import com.vishop.iam.modules.identity.presentation.rest.dto.RegisterUserRequest;
import com.vishop.iam.modules.identity.presentation.rest.dto.SwitchTenantRequest;
import io.github.coreplatform.common.domain.cqrs.CommandBus;
import io.github.coreplatform.common.domain.cqrs.QueryBus;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
  private final CommandBus commands;
  private final QueryBus queries;
  private final boolean secure;
  private final long sessionSeconds;

  public AuthController(
      CommandBus commands,
      QueryBus queries,
      @Value("${vishop.security.cookie.secure:true}") boolean secure,
      @Value("${vishop.security.session-seconds:604800}") long sessionSeconds) {
    this.commands = commands;
    this.queries = queries;
    this.secure = secure;
    this.sessionSeconds = sessionSeconds;
  }

  @PostMapping("/register")
  public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterUserRequest request) {
    var result =
        commands.dispatch(
            new RegisterUserCommand(
                request.email(),
                request.password(),
                request.fullName(),
                request.tenantId(),
                request.role()));
    return authenticated(result, HttpStatus.CREATED);
  }

  @PostMapping("/login")
  public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
    return authenticated(
        commands.dispatch(new LoginCommand(request.email(), request.password())), HttpStatus.OK);
  }

  private ResponseEntity<AuthResponse> authenticated(
      AuthenticationResult result, HttpStatus status) {
    return ResponseEntity.status(status)
        .cacheControl(CacheControl.noStore())
        .header(HttpHeaders.SET_COOKIE, cookie(result.sessionToken(), sessionSeconds).toString())
        .body(result.response());
  }

  private ResponseCookie cookie(String value, long seconds) {
    return ResponseCookie.from(IamAuthenticationFilter.COOKIE, value)
        .httpOnly(true)
        .secure(secure)
        .sameSite("Lax")
        .path("/")
        .maxAge(seconds)
        .build();
  }

  @GetMapping("/me")
  public UserView me(@AuthenticationPrincipal IamPrincipal actor) {
    return queries.dispatch(new GetUserProfileQuery(actor.actor()));
  }

  @PostMapping("/switch-tenant")
  public ResponseEntity<AuthResponse> switchTenant(
      @AuthenticationPrincipal IamPrincipal actor,
      @Valid @RequestBody SwitchTenantRequest request) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(commands.dispatch(new SwitchTenantCommand(actor.actor(), request.tenantId())));
  }

  @PostMapping("/refresh")
  public ResponseEntity<AuthResponse> refresh(@AuthenticationPrincipal IamPrincipal actor) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(queries.dispatch(new RefreshAccessTokenQuery(actor.actor())));
  }

  @PostMapping("/logout")
  public ResponseEntity<Void> logout(@AuthenticationPrincipal IamPrincipal actor) {
    commands.dispatch(new LogoutCommand(actor.actor()));
    return ResponseEntity.noContent()
        .header(HttpHeaders.SET_COOKIE, cookie("", 0).toString())
        .build();
  }
}
