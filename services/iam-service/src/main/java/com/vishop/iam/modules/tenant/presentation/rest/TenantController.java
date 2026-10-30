package com.vishop.iam.modules.tenant.presentation.rest;

import com.vishop.iam.modules.tenant.application.api.TenantApi.Status;
import com.vishop.iam.modules.tenant.application.command.tenant.CreateTenantCommand;
import com.vishop.iam.modules.tenant.application.command.tenant.RenameTenantCommand;
import com.vishop.iam.modules.tenant.application.dto.TenantListCriteria;
import com.vishop.iam.modules.tenant.application.dto.TenantResponseDto;
import com.vishop.iam.modules.tenant.application.query.tenant.GetTenantByIdQuery;
import com.vishop.iam.modules.tenant.application.query.tenant.ListMyTenantsQuery;
import com.vishop.iam.modules.tenant.presentation.rest.dto.CreateTenantRequest;
import com.vishop.iam.modules.tenant.presentation.rest.dto.RenameTenantRequest;
import com.vishop.iam.shared.application.dto.AuthenticatedUser;
import io.github.coreplatform.common.domain.constants.ApiPaths;
import io.github.coreplatform.common.domain.cqrs.CommandBus;
import io.github.coreplatform.common.domain.cqrs.QueryBus;
import io.github.coreplatform.common.domain.exception.BadRequestException;
import io.github.coreplatform.common.web.pagination.ListEnvelope;
import io.github.coreplatform.common.web.pagination.PaginationRequest;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.Instant;
import java.util.UUID;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(ApiPaths.V1 + "/tenants")
public class TenantController {
  private final CommandBus commands;
  private final QueryBus queries;

  public TenantController(CommandBus commands, QueryBus queries) {
    this.commands = commands;
    this.queries = queries;
  }

  @GetMapping
  public ListEnvelope<TenantResponseDto> mine(
      @AuthenticationPrincipal AuthenticatedUser actor,
      @RequestParam(required = false) UUID id,
      @RequestParam(required = false) String code,
      @RequestParam(required = false) String name,
      @RequestParam(required = false) Status status,
      @RequestParam(required = false) Instant createdFrom,
      @RequestParam(required = false) Instant createdTo,
      @RequestParam(defaultValue = "createdAt:asc") String sort,
      @RequestParam(defaultValue = "offset") String paginationMode,
      @ParameterObject PaginationRequest pagination) {
    if (!"offset".equalsIgnoreCase(paginationMode) && !"cursor".equalsIgnoreCase(paginationMode)) {
      throw new BadRequestException("paginationMode must be offset or cursor");
    }
    boolean cursorMode = pagination.hasCursor() || "cursor".equalsIgnoreCase(paginationMode);
    return ListEnvelope.from(
        ApiPaths.V1 + "/tenants",
        queries.dispatch(
            new ListMyTenantsQuery(
                actor,
                cursorMode ? null : pagination.toOffsetPagination(),
                cursorMode ? pagination.toCursorParams() : null,
                TenantListCriteria.fromSort(
                    id, code, name, status, createdFrom, createdTo, sort))));
  }

  @GetMapping("/{id}")
  @PreAuthorize(
      "@iamAuthorization.inTenant(authentication, #id) and hasAuthority(T(io.github.coreplatform.common.security.constants.CommonPermissions.Tenant).VIEW)")
  public TenantResponseDto get(
      @AuthenticationPrincipal AuthenticatedUser actor, @PathVariable UUID id) {
    return queries.dispatch(new GetTenantByIdQuery(actor, id));
  }

  @PatchMapping("/{id}")
  @PreAuthorize(
      "@iamAuthorization.inTenant(authentication, #id) and hasAuthority(T(io.github.coreplatform.common.security.constants.CommonPermissions.Tenant).MANAGE)")
  public TenantResponseDto rename(
      @AuthenticationPrincipal AuthenticatedUser actor,
      @PathVariable UUID id,
      @Valid @RequestBody RenameTenantRequest request) {
    return commands.dispatch(new RenameTenantCommand(actor, id, request.name()));
  }

  @PostMapping
  public ResponseEntity<TenantResponseDto> create(
      @AuthenticationPrincipal AuthenticatedUser actor,
      @Valid @RequestBody CreateTenantRequest request) {
    var tenant = commands.dispatch(new CreateTenantCommand(actor, request.code(), request.name()));
    return ResponseEntity.created(URI.create(ApiPaths.V1 + "/tenants/" + tenant.id())).body(tenant);
  }
}
