package com.vishop.iam.composition.onboarding.application.command;

import com.vishop.iam.modules.access.application.api.AccessApi;
import com.vishop.iam.modules.identity.application.api.IdentityApi;
import com.vishop.iam.modules.tenant.application.api.TenantApi;
import com.vishop.iam.modules.tenant.application.dto.TenantResponseDto;
import io.github.coreplatform.common.domain.cqrs.CommandHandler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class CreateTenantCommandHandler
    implements CommandHandler<CreateTenantCommand, TenantResponseDto> {
  private final IdentityApi identities;
  private final TenantApi tenants;
  private final AccessApi access;

  public CreateTenantCommandHandler(IdentityApi identities, TenantApi tenants, AccessApi access) {
    this.identities = identities;
    this.tenants = tenants;
    this.access = access;
  }

  @Override
  @Transactional
  public TenantResponseDto handle(CreateTenantCommand command) {
    identities.requireActive(command.actor().userId());
    var tenant = tenants.create(command.actor().userId(), command.code(), command.name());
    access.initializeOwner(command.actor().userId(), tenant.id());
    return tenant;
  }
}
