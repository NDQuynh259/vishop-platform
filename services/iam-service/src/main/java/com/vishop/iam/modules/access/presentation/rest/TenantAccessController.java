package com.vishop.iam.modules.access.presentation.rest;

import com.vishop.iam.infra.security.IamPrincipal;
import com.vishop.iam.modules.access.application.command.ChangeMemberRoleCommand;
import com.vishop.iam.modules.access.application.command.CreateRoleCommand;
import com.vishop.iam.modules.access.application.command.DeactivateMemberCommand;
import com.vishop.iam.modules.access.application.command.InviteMemberCommand;
import com.vishop.iam.modules.access.application.command.UpdateRoleCommand;
import com.vishop.iam.modules.access.application.dto.InvitationCreated;
import com.vishop.iam.modules.access.application.dto.MemberView;
import com.vishop.iam.modules.access.application.dto.RoleResponseDto;
import com.vishop.iam.modules.access.application.query.ListTenantMembersQuery;
import com.vishop.iam.modules.access.application.query.ListTenantRolesQuery;
import com.vishop.iam.modules.access.presentation.rest.dto.ChangeMemberRoleRequest;
import com.vishop.iam.modules.access.presentation.rest.dto.InviteMemberRequest;
import com.vishop.iam.modules.access.presentation.rest.dto.RoleRequest;
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

  @GetMapping("/{id}/members")
  public List<MemberView> members(
      @AuthenticationPrincipal IamPrincipal actor, @PathVariable UUID id) {
    return queries.dispatch(new ListTenantMembersQuery(actor.actor(), id));
  }

  @PutMapping("/{id}/members/{user}/role")
  public ResponseEntity<Void> assign(
      @AuthenticationPrincipal IamPrincipal actor,
      @PathVariable UUID id,
      @PathVariable UUID user,
      @Valid @RequestBody ChangeMemberRoleRequest request) {
    commands.dispatch(new ChangeMemberRoleCommand(actor.actor(), id, user, request.roleId()));
    return ResponseEntity.noContent().build();
  }

  @DeleteMapping("/{id}/members/{user}")
  public ResponseEntity<Void> deactivate(
      @AuthenticationPrincipal IamPrincipal actor, @PathVariable UUID id, @PathVariable UUID user) {
    commands.dispatch(new DeactivateMemberCommand(actor.actor(), id, user));
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/{id}/roles")
  public List<RoleResponseDto> roles(
      @AuthenticationPrincipal IamPrincipal actor, @PathVariable UUID id) {
    return queries.dispatch(new ListTenantRolesQuery(actor.actor(), id));
  }

  @PostMapping("/{id}/roles")
  public ResponseEntity<RoleResponseDto> createRole(
      @AuthenticationPrincipal IamPrincipal actor,
      @PathVariable UUID id,
      @Valid @RequestBody RoleRequest request) {
    var role =
        commands.dispatch(
            new CreateRoleCommand(actor.actor(), id, request.name(), request.permissions()));
    return ResponseEntity.created(
            URI.create(ApiPaths.V1 + "/tenants/" + id + "/roles/" + role.id()))
        .body(role);
  }

  @PutMapping("/{id}/roles/{role}")
  public RoleResponseDto editRole(
      @AuthenticationPrincipal IamPrincipal actor,
      @PathVariable UUID id,
      @PathVariable UUID role,
      @Valid @RequestBody RoleRequest request) {
    return commands.dispatch(
        new UpdateRoleCommand(actor.actor(), id, role, request.name(), request.permissions()));
  }

  @PostMapping("/{id}/invitations")
  public ResponseEntity<InvitationCreated> invite(
      @AuthenticationPrincipal IamPrincipal actor,
      @PathVariable UUID id,
      @Valid @RequestBody InviteMemberRequest request) {
    return ResponseEntity.status(201)
        .cacheControl(org.springframework.http.CacheControl.noStore())
        .body(
            commands.dispatch(
                new InviteMemberCommand(actor.actor(), id, request.email(), request.roleId())));
  }
}
