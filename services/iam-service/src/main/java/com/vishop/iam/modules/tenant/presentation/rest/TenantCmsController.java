package com.vishop.iam.modules.tenant.presentation.rest;

import com.vishop.iam.contracts.tenant.TenantResponseDto;
import com.vishop.iam.infra.security.IamPrincipal;
import com.vishop.iam.modules.tenant.application.command.ChangeTenantStatusCommand;
import com.vishop.iam.modules.tenant.application.query.ListCmsTenantsQuery;
import com.vishop.iam.modules.tenant.presentation.rest.dto.ChangeTenantStatusRequest;
import io.github.coreplatform.common.domain.cqrs.CommandBus;
import io.github.coreplatform.common.domain.cqrs.QueryBus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/cms")
@Validated
public class TenantCmsController {
  private final CommandBus commands;
  private final QueryBus queries;

  public TenantCmsController(CommandBus commands, QueryBus queries) {
    this.commands = commands;
    this.queries = queries;
  }

  @GetMapping("/tenants")
  public List<TenantResponseDto> tenants(
      @AuthenticationPrincipal IamPrincipal actor,
      @RequestParam(defaultValue = "50") @Min(1) @Max(100) int limit,
      @RequestParam(defaultValue = "0") @Min(0) int offset) {
    return queries.dispatch(new ListCmsTenantsQuery(actor.actor(), limit, offset));
  }

  @PatchMapping("/tenants/{id}/status")
  public TenantResponseDto tenantStatus(
      @AuthenticationPrincipal IamPrincipal actor,
      @PathVariable UUID id,
      @Valid @RequestBody ChangeTenantStatusRequest request) {
    return commands.dispatch(new ChangeTenantStatusCommand(actor.actor(), id, request.status()));
  }
}
