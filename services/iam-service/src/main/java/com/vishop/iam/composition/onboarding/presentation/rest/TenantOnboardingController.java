package com.vishop.iam.composition.onboarding.presentation.rest;

import com.vishop.iam.composition.onboarding.application.command.CreateTenantCommand;
import com.vishop.iam.composition.onboarding.presentation.rest.dto.CreateTenantRequest;
import com.vishop.iam.infra.security.IamPrincipal;
import com.vishop.iam.modules.tenant.application.dto.TenantResponseDto;
import io.github.coreplatform.common.domain.cqrs.CommandBus;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/tenants")
public class TenantOnboardingController {
  private final CommandBus commands;

  public TenantOnboardingController(CommandBus commands) {
    this.commands = commands;
  }

  @PostMapping
  public ResponseEntity<TenantResponseDto> create(
      @AuthenticationPrincipal IamPrincipal actor,
      @Valid @RequestBody CreateTenantRequest request) {
    var tenant =
        commands.dispatch(new CreateTenantCommand(actor.actor(), request.code(), request.name()));
    return ResponseEntity.created(URI.create("/api/v1/tenants/" + tenant.id())).body(tenant);
  }
}
