package com.vishop.iam.modules.access.presentation.rest;

import com.vishop.iam.contracts.tenant.TenantResponseDto;
import com.vishop.iam.modules.access.application.command.AcceptInvitationCommand;
import com.vishop.iam.modules.access.presentation.rest.dto.AcceptInvitationRequest;
import com.vishop.iam.shared.infrastructure.security.IamPrincipal;
import io.github.coreplatform.common.domain.cqrs.CommandBus;
import io.github.coreplatform.common.domain.cqrs.QueryBus;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/invitations")
public class InvitationController {
  private final CommandBus commands;
  private final QueryBus queries;

  public InvitationController(CommandBus commands, QueryBus queries) {
    this.commands = commands;
    this.queries = queries;
  }

  @PostMapping("/accept")
  public TenantResponseDto accept(
      @AuthenticationPrincipal IamPrincipal actor,
      @Valid @RequestBody AcceptInvitationRequest request) {
    return commands.dispatch(new AcceptInvitationCommand(actor.actor(), request.token()));
  }
}
