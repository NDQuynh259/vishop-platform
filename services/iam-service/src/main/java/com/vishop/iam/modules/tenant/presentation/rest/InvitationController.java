package com.vishop.iam.modules.tenant.presentation.rest;

import com.vishop.iam.modules.tenant.application.command.invitation.AcceptInvitationCommand;
import com.vishop.iam.modules.tenant.application.dto.TenantResponseDto;
import com.vishop.iam.modules.tenant.presentation.rest.dto.AcceptInvitationRequest;
import com.vishop.iam.shared.application.dto.AuthenticatedUser;
import io.github.coreplatform.common.domain.constants.ApiPaths;
import io.github.coreplatform.common.domain.cqrs.CommandBus;
import io.github.coreplatform.common.domain.cqrs.QueryBus;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(ApiPaths.V1 + "/invitations")
public class InvitationController {
  private final CommandBus commands;
  private final QueryBus queries;

  public InvitationController(CommandBus commands, QueryBus queries) {
    this.commands = commands;
    this.queries = queries;
  }

  @PostMapping("/accept")
  public TenantResponseDto accept(
      @AuthenticationPrincipal AuthenticatedUser actor,
      @Valid @RequestBody AcceptInvitationRequest request) {
    return commands.dispatch(new AcceptInvitationCommand(actor, request.token()));
  }
}
