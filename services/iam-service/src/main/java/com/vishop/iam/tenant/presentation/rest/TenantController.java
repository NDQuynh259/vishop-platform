package com.vishop.iam.tenant.presentation.rest;

import com.vishop.iam.shared.infrastructure.security.IamPrincipal;
import com.vishop.iam.tenant.application.command.CreateTenantCommand;
import com.vishop.iam.tenant.application.command.RenameTenantCommand;
import com.vishop.iam.tenant.application.dto.CreateTenantRequest;
import com.vishop.iam.tenant.application.dto.RenameTenantRequest;
import com.vishop.iam.tenant.application.dto.TenantResponseDto;
import com.vishop.iam.tenant.application.query.GetTenantByIdQuery;
import io.github.coreplatform.common.domain.cqrs.CommandBus;
import io.github.coreplatform.common.domain.cqrs.QueryBus;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
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

  @PostMapping
  public ResponseEntity<TenantResponseDto> create(
      @AuthenticationPrincipal IamPrincipal actor,
      @Valid @RequestBody CreateTenantRequest request) {
    var tenant =
        commands.dispatch(new CreateTenantCommand(actor.actor(), request.code(), request.name()));
    return ResponseEntity.created(URI.create("/api/v1/tenants/" + tenant.id())).body(tenant);
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
