package com.vishop.iam.modules.identity.presentation.rest;

import com.vishop.iam.modules.identity.application.command.auth.*;
import com.vishop.iam.modules.identity.application.dto.AuthResult;
import com.vishop.iam.modules.identity.presentation.rest.dto.*;
import com.vishop.iam.shared.application.dto.AuthenticatedUser;
import io.github.coreplatform.common.domain.constants.ApiPaths;
import io.github.coreplatform.common.domain.cqrs.CommandBus;
import io.github.coreplatform.common.security.constants.AuthCookies;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(ApiPaths.V1 + "/auth")
public class AuthController {
  private final CommandBus commands;
  private final AuthenticationCookies cookies;

  public AuthController(CommandBus commands, AuthenticationCookies cookies) {
    this.commands = commands;
    this.cookies = cookies;
  }

  /** Tạo user + phiên trong transaction; response chỉ đưa credential vào HttpOnly cookie. */
  @PostMapping("/register")
  public ResponseEntity<AuthenticationView> register(
      @Valid @RequestBody RegisterUserRequest request) {
    return authenticated(
        commands.dispatch(new RegisterCommand(request.email(), request.password())),
        HttpStatus.CREATED);
  }

  /** Password/status hợp lệ mới tạo sid mới; mỗi thiết bị giữ một sid độc lập. */
  @PostMapping("/login")
  public ResponseEntity<AuthenticationView> login(@Valid @RequestBody LoginRequest request) {
    return authenticated(
        commands.dispatch(
            new LoginCommand(request.email(), request.password(), request.tenantId())),
        HttpStatus.OK);
  }

  /** Access hết hạn vẫn refresh được. tenantId tùy chọn được kiểm tra membership trong DB. */
  @PostMapping("/refresh")
  public ResponseEntity<AuthenticationView> refresh(
      HttpServletRequest http, @RequestBody(required = false) RefreshRequest request) {
    return authenticated(
        commands.dispatch(
            new RefreshCommand(
                cookies.read(http, AuthCookies.REFRESH_TOKEN, true),
                request == null ? null : request.tenantId())),
        HttpStatus.OK);
  }

  /** Thu hồi sid và xác nhận Redis trước khi xóa cookie; request lặp không nhân audit. */
  @PostMapping("/logout")
  public ResponseEntity<Void> logout(
      @AuthenticationPrincipal AuthenticatedUser actor, HttpServletRequest request) {
    commands.dispatch(
        new LogoutCommand(
            actor,
            cookies.read(request, AuthCookies.REFRESH_TOKEN, false),
            cookies.read(request, AuthCookies.ACCESS_TOKEN, false)));
    return ResponseEntity.noContent()
        .headers(cookies.clear())
        .cacheControl(CacheControl.noStore())
        .build();
  }

  private ResponseEntity<AuthenticationView> authenticated(AuthResult result, HttpStatus status) {
    return ResponseEntity.status(status)
        .headers(cookies.issue(result.tokens()))
        .cacheControl(CacheControl.noStore())
        .body(AuthenticationView.from(result));
  }
}
