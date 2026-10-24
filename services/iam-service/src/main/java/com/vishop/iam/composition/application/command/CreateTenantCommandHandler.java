package com.vishop.iam.composition.application.command;

import com.vishop.iam.contracts.access.AccessApi;
import com.vishop.iam.contracts.identity.IdentityApi;
import com.vishop.iam.contracts.tenant.TenantApi;
import com.vishop.iam.contracts.tenant.TenantResponseDto;
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
