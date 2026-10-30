package com.vishop.iam.modules.identity.presentation.rest;

import com.vishop.iam.modules.identity.application.command.cms.ChangeSystemRoleCommand;
import com.vishop.iam.modules.identity.application.command.cms.ChangeUserStatusCommand;
import com.vishop.iam.modules.identity.application.dto.UserView;
import com.vishop.iam.modules.identity.application.query.ListCmsUsersQuery;
import com.vishop.iam.modules.identity.presentation.rest.dto.ChangeSystemRoleRequest;
import com.vishop.iam.modules.identity.presentation.rest.dto.ChangeUserStatusRequest;
import com.vishop.iam.shared.application.dto.AuthenticatedUser;
import io.github.coreplatform.common.domain.constants.ApiPaths;
import io.github.coreplatform.common.domain.cqrs.CommandBus;
import io.github.coreplatform.common.domain.cqrs.QueryBus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(ApiPaths.V1 + "/cms")
@Validated
public class UserCmsController {
  private final CommandBus commands;
  private final QueryBus queries;

  public UserCmsController(CommandBus commands, QueryBus queries) {
    this.commands = commands;
    this.queries = queries;
  }

  @GetMapping("/users")
  public List<UserView> users(
      @AuthenticationPrincipal AuthenticatedUser actor,
      @RequestParam(defaultValue = "50") @Min(1) @Max(100) int limit,
      @RequestParam(defaultValue = "0") @Min(0) int offset) {
    return queries.dispatch(new ListCmsUsersQuery(actor, limit, offset));
  }

  @PatchMapping("/users/{id}/system-role")
  public ResponseEntity<Void> role(
      @AuthenticationPrincipal AuthenticatedUser actor,
      @PathVariable UUID id,
      @Valid @RequestBody ChangeSystemRoleRequest request) {
    commands.dispatch(new ChangeSystemRoleCommand(actor, id, request.systemRole()));
    return ResponseEntity.noContent().build();
  }

  @PatchMapping("/users/{id}/status")
  public ResponseEntity<Void> userStatus(
      @AuthenticationPrincipal AuthenticatedUser actor,
      @PathVariable UUID id,
      @Valid @RequestBody ChangeUserStatusRequest request) {
    commands.dispatch(new ChangeUserStatusCommand(actor, id, request.status()));
    return ResponseEntity.noContent().build();
  }
}
