package com.vishop.iam.modules.tenant.presentation.rest;

import com.vishop.iam.modules.tenant.application.command.tenant.UpdateTenantCommand;
import com.vishop.iam.modules.tenant.application.dto.TenantResponseDto;
import com.vishop.iam.modules.tenant.application.query.tenant.ListCmsTenantsQuery;
import com.vishop.iam.modules.tenant.presentation.rest.dto.UpdateTenantRequest;
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
import org.springframework.security.access.prepost.PreAuthorize;
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
  @PreAuthorize("@iamAuthorization.canAccessCms(authentication)")
  public ListEnvelope<TenantResponseDto> tenants(
      @AuthenticationPrincipal AuthenticatedUser actor,
      @ParameterObject PaginationRequest pagination) {
    return ListEnvelope.from(
        ApiPaths.V1 + "/cms/tenants",
        queries.dispatch(new ListCmsTenantsQuery(actor, pagination.toPagination())));
  }

  @PutMapping("/tenants/{id}")
  @PreAuthorize("@iamAuthorization.canAccessCms(authentication)")
  public TenantResponseDto updateTenant(
      @AuthenticationPrincipal AuthenticatedUser actor,
      @PathVariable UUID id,
      @Valid @RequestBody UpdateTenantRequest request) {
    return commands.dispatch(new UpdateTenantCommand(actor, id, request.name(), request.status()));
  }
}
