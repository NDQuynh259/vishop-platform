package com.vishop.iam.modules.identity.presentation.rest;

import com.vishop.iam.infra.security.IamPrincipal;
import com.vishop.iam.modules.identity.application.command.login.LoginCommand;
import com.vishop.iam.modules.identity.application.command.logout.LogoutCommand;
import com.vishop.iam.modules.identity.application.command.register.RegisterUserCommand;
import com.vishop.iam.modules.identity.application.command.switchtenant.SwitchTenantCommand;
import com.vishop.iam.modules.identity.application.dto.AuthResponse;
import com.vishop.iam.modules.identity.application.dto.UserView;
import com.vishop.iam.modules.identity.application.query.GetUserProfileQuery;
import com.vishop.iam.modules.identity.application.query.RefreshAccessTokenQuery;
import com.vishop.iam.modules.identity.presentation.rest.dto.AuthenticationView;
import com.vishop.iam.modules.identity.presentation.rest.dto.LoginRequest;
import com.vishop.iam.modules.identity.presentation.rest.dto.RegisterUserRequest;
import com.vishop.iam.modules.identity.presentation.rest.dto.SwitchTenantRequest;
import io.github.coreplatform.common.domain.cqrs.CommandBus;
import io.github.coreplatform.common.domain.cqrs.QueryBus;
import io.github.coreplatform.common.security.constants.AuthCookies;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
  private final CommandBus commands;
  private final QueryBus queries;
  private final AuthenticationCookies cookies;

  public AuthController(CommandBus commands, QueryBus queries, AuthenticationCookies cookies) {
    this.commands = commands;
    this.queries = queries;
    this.cookies = cookies;
  }

  @PostMapping("/register")
  public ResponseEntity<AuthenticationView> register(
      @Valid @RequestBody RegisterUserRequest request) {
    var result = commands.dispatch(new RegisterUserCommand(request.email(), request.password()));
    return authenticated(result, HttpStatus.CREATED);
  }

  @PostMapping("/login")
  public ResponseEntity<AuthenticationView> login(@Valid @RequestBody LoginRequest request) {
    return authenticated(
        commands.dispatch(new LoginCommand(request.email(), request.password())), HttpStatus.OK);
  }

  private ResponseEntity<AuthenticationView> authenticated(AuthResponse result, HttpStatus status) {
    return ResponseEntity.status(status)
        .headers(cookies.issue(result))
        .cacheControl(CacheControl.noStore())
        .body(AuthenticationView.from(result));
  }

  @GetMapping("/me")
  public UserView me(@AuthenticationPrincipal IamPrincipal actor) {
    return queries.dispatch(new GetUserProfileQuery(actor.actor()));
  }

  @PostMapping("/switch-tenant")
  public ResponseEntity<AuthenticationView> switchTenant(
      @AuthenticationPrincipal IamPrincipal actor,
      @Valid @RequestBody SwitchTenantRequest request,
      @CookieValue(name = AuthCookies.REFRESH_TOKEN, required = false) String refreshToken) {
    return authenticated(
        commands.dispatch(
            new SwitchTenantCommand(
                actor.actor(), request.tenantId(), cookies.requireRefresh(refreshToken))),
        HttpStatus.OK);
  }

  @PostMapping("/refresh")
  public ResponseEntity<AuthenticationView> refresh(
      @CookieValue(name = AuthCookies.REFRESH_TOKEN, required = false) String refreshToken) {
    return authenticated(
        queries.dispatch(new RefreshAccessTokenQuery(cookies.requireRefresh(refreshToken))),
        HttpStatus.OK);
  }

  @PostMapping("/logout")
  public ResponseEntity<Void> logout(@AuthenticationPrincipal IamPrincipal actor) {
    if (actor != null) commands.dispatch(new LogoutCommand(actor.actor()));
    return ResponseEntity.noContent()
        .headers(cookies.clear())
        .cacheControl(CacheControl.noStore())
        .build();
  }
}
