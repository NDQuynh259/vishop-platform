package com.vishop.iam.modules.tenant.presentation.rest;

import com.vishop.iam.contracts.tenant.TenantResponseDto;
import com.vishop.iam.infra.security.IamPrincipal;
import com.vishop.iam.modules.tenant.application.command.RenameTenantCommand;
import com.vishop.iam.modules.tenant.application.query.GetTenantByIdQuery;
import com.vishop.iam.modules.tenant.presentation.rest.dto.RenameTenantRequest;
import io.github.coreplatform.common.domain.cqrs.CommandBus;
import io.github.coreplatform.common.domain.cqrs.QueryBus;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/tenants")
public class TenantController {
  private final CommandBus commands;
  private final QueryBus queries;

  public TenantController(CommandBus commands, QueryBus queries) {
    this.commands = commands;
    this.queries = queries;
  }

  @GetMapping("/{id}")
  public TenantResponseDto get(@AuthenticationPrincipal IamPrincipal actor, @PathVariable UUID id) {
    return queries.dispatch(new GetTenantByIdQuery(actor.actor(), id));
  }

  @PatchMapping("/{id}")
  public TenantResponseDto rename(
      @AuthenticationPrincipal IamPrincipal actor,
      @PathVariable UUID id,
      @Valid @RequestBody RenameTenantRequest request) {
    return commands.dispatch(new RenameTenantCommand(actor.actor(), id, request.name()));
  }
}
