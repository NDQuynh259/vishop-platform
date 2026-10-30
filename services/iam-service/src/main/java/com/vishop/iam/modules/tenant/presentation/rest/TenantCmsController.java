package com.vishop.iam.modules.tenant.presentation.rest;

import com.vishop.iam.modules.tenant.application.command.tenant.UpdateTenantCommand;
import com.vishop.iam.modules.tenant.application.dto.TenantResponseDto;
import com.vishop.iam.modules.tenant.application.query.tenant.ListCmsTenantsQuery;
import com.vishop.iam.modules.tenant.presentation.rest.dto.UpdateTenantRequest;
import com.vishop.iam.shared.application.dto.AuthenticatedUser;
import io.github.coreplatform.common.domain.constants.ApiPaths;
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
@RequestMapping(ApiPaths.V1 + "/cms")
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
      @AuthenticationPrincipal AuthenticatedUser actor,
      @RequestParam(defaultValue = "50") @Min(1) @Max(100) int limit,
      @RequestParam(defaultValue = "0") @Min(0) int offset) {
    return queries.dispatch(new ListCmsTenantsQuery(actor, limit, offset));
  }

  @PutMapping("/tenants/{id}")
  public TenantResponseDto updateTenant(
      @AuthenticationPrincipal AuthenticatedUser actor,
      @PathVariable UUID id,
      @Valid @RequestBody UpdateTenantRequest request) {
    return commands.dispatch(new UpdateTenantCommand(actor, id, request.name(), request.status()));
  }
}
