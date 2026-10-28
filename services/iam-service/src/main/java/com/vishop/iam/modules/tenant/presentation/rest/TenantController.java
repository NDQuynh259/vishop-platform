package com.vishop.iam.modules.tenant.presentation.rest;

import com.vishop.iam.infra.security.IamPrincipal;
import com.vishop.iam.modules.tenant.application.command.RenameTenantCommand;
import com.vishop.iam.modules.tenant.application.dto.TenantListCriteria;
import com.vishop.iam.modules.tenant.application.dto.TenantResponseDto;
import com.vishop.iam.modules.tenant.application.query.GetTenantByIdQuery;
import com.vishop.iam.modules.tenant.application.query.ListMyTenantsQuery;
import com.vishop.iam.modules.tenant.domain.valueobject.TenantStatus;
import com.vishop.iam.modules.tenant.presentation.rest.dto.RenameTenantRequest;
import io.github.coreplatform.common.domain.constants.ApiPaths;
import io.github.coreplatform.common.domain.cqrs.CommandBus;
import io.github.coreplatform.common.domain.cqrs.QueryBus;
import io.github.coreplatform.common.domain.exception.BadRequestException;
import io.github.coreplatform.common.web.pagination.ListEnvelope;
import io.github.coreplatform.common.web.pagination.PaginationRequest;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.UUID;
import org.springdoc.core.annotations.ParameterObject;
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
      @AuthenticationPrincipal IamPrincipal actor,
      @RequestParam(required = false) UUID id,
      @RequestParam(required = false) String code,
      @RequestParam(required = false) String name,
      @RequestParam(required = false) TenantStatus status,
      @RequestParam(required = false) Instant createdFrom,
      @RequestParam(required = false) Instant createdTo,
      @RequestParam(defaultValue = "createdAt:asc") String sort,
      @ParameterObject PaginationRequest pagination) {
    if (pagination.hasCursor()) {
      throw new BadRequestException("Tenant list supports offset pagination; use page and limit");
    }
    return ListEnvelope.from(
        ApiPaths.V1 + "/tenants",
        queries.dispatch(
            new ListMyTenantsQuery(
                actor.actor(),
                pagination.toOffsetPagination(),
                TenantListCriteria.fromSort(
                    id, code, name, status, createdFrom, createdTo, sort))));
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
