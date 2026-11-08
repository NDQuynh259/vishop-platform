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
import io.github.coreplatform.common.web.pagination.ListEnvelope;
import io.github.coreplatform.common.web.pagination.PaginationRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.UUID;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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
  @PreAuthorize("@iamAuthorization.canAccessCms(authentication)")
  public ListEnvelope<UserView> users(
      @AuthenticationPrincipal AuthenticatedUser actor,
      @ParameterObject PaginationRequest pagination) {
    return ListEnvelope.from(
        ApiPaths.V1 + "/cms/users",
        queries.dispatch(new ListCmsUsersQuery(actor, pagination.toPagination())));
  }

  @PatchMapping("/users/{id}/system-role")
  @PreAuthorize("@iamAuthorization.canAccessCms(authentication)")
  public ResponseEntity<Void> role(
      @AuthenticationPrincipal AuthenticatedUser actor,
      @PathVariable UUID id,
      @Valid @RequestBody ChangeSystemRoleRequest request) {
    commands.dispatch(new ChangeSystemRoleCommand(actor, id, request.systemRole()));
    return ResponseEntity.noContent().build();
  }

  @PatchMapping("/users/{id}/status")
  @PreAuthorize("@iamAuthorization.canAccessCms(authentication)")
  public ResponseEntity<Void> userStatus(
      @AuthenticationPrincipal AuthenticatedUser actor,
      @PathVariable UUID id,
      @Valid @RequestBody ChangeUserStatusRequest request) {
    commands.dispatch(new ChangeUserStatusCommand(actor, id, request.status()));
    return ResponseEntity.noContent().build();
  }
}
