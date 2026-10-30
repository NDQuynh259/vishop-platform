package com.vishop.iam.modules.tenant.presentation.rest;

import com.vishop.iam.modules.tenant.application.command.member.ChangeMemberRoleCommand;
import com.vishop.iam.modules.tenant.application.command.member.DeactivateMemberCommand;
import com.vishop.iam.modules.tenant.application.command.member.InviteMemberCommand;
import com.vishop.iam.modules.tenant.application.command.role.CreateRoleCommand;
import com.vishop.iam.modules.tenant.application.command.role.UpdateRoleCommand;
import com.vishop.iam.modules.tenant.application.dto.InvitationCreated;
import com.vishop.iam.modules.tenant.application.dto.RoleResponseDto;
import com.vishop.iam.modules.tenant.application.query.role.ListTenantRolesQuery;
import com.vishop.iam.modules.tenant.presentation.rest.dto.ChangeMemberRoleRequest;
import com.vishop.iam.modules.tenant.presentation.rest.dto.InviteMemberRequest;
import com.vishop.iam.modules.tenant.presentation.rest.dto.RoleRequest;
import com.vishop.iam.shared.application.dto.AuthenticatedUser;
import io.github.coreplatform.common.domain.constants.ApiPaths;
import io.github.coreplatform.common.domain.cqrs.CommandBus;
import io.github.coreplatform.common.domain.cqrs.QueryBus;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(ApiPaths.V1 + "/tenants")
public class TenantAccessController {
  private final CommandBus commands;
  private final QueryBus queries;

  public TenantAccessController(CommandBus commands, QueryBus queries) {
    this.commands = commands;
    this.queries = queries;
  }

  @PutMapping("/{id}/members/{user}/role")
  public ResponseEntity<Void> assign(
      @AuthenticationPrincipal AuthenticatedUser actor,
      @PathVariable UUID id,
      @PathVariable UUID user,
      @Valid @RequestBody ChangeMemberRoleRequest request) {
    commands.dispatch(new ChangeMemberRoleCommand(actor, id, user, request.roleId()));
    return ResponseEntity.noContent().build();
  }

  @DeleteMapping("/{id}/members/{user}")
  public ResponseEntity<Void> deactivate(
      @AuthenticationPrincipal AuthenticatedUser actor,
      @PathVariable UUID id,
      @PathVariable UUID user) {
    commands.dispatch(new DeactivateMemberCommand(actor, id, user));
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/{id}/roles")
  public List<RoleResponseDto> roles(
      @AuthenticationPrincipal AuthenticatedUser actor, @PathVariable UUID id) {
    return queries.dispatch(new ListTenantRolesQuery(actor, id));
  }

  @PostMapping("/{id}/roles")
  public ResponseEntity<RoleResponseDto> createRole(
      @AuthenticationPrincipal AuthenticatedUser actor,
      @PathVariable UUID id,
      @Valid @RequestBody RoleRequest request) {
    var role =
        commands.dispatch(new CreateRoleCommand(actor, id, request.name(), request.permissions()));
    return ResponseEntity.created(
            URI.create(ApiPaths.V1 + "/tenants/" + id + "/roles/" + role.id()))
        .body(role);
  }

  @PutMapping("/{id}/roles/{role}")
  public RoleResponseDto editRole(
      @AuthenticationPrincipal AuthenticatedUser actor,
      @PathVariable UUID id,
      @PathVariable UUID role,
      @Valid @RequestBody RoleRequest request) {
    return commands.dispatch(
        new UpdateRoleCommand(actor, id, role, request.name(), request.permissions()));
  }

  @PostMapping("/{id}/invitations")
  public ResponseEntity<InvitationCreated> invite(
      @AuthenticationPrincipal AuthenticatedUser actor,
      @PathVariable UUID id,
      @Valid @RequestBody InviteMemberRequest request) {
    return ResponseEntity.status(201)
        .cacheControl(org.springframework.http.CacheControl.noStore())
        .body(
            commands.dispatch(
                new InviteMemberCommand(actor, id, request.email(), request.roleId())));
  }
}
