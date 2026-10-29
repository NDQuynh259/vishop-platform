package com.vishop.iam.modules.tenant.application.command.tenant;

import com.vishop.iam.modules.tenant.application.api.TenantApi;
import com.vishop.iam.modules.tenant.application.dto.TenantResponseDto;
import io.github.coreplatform.common.domain.cqrs.CommandHandler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class CreateTenantCommandHandler
    implements CommandHandler<CreateTenantCommand, TenantResponseDto> {
  private final TenantApi tenants;

  public CreateTenantCommandHandler(TenantApi tenants) {
    this.tenants = tenants;
  }

  @Override
  @Transactional
  public TenantResponseDto handle(CreateTenantCommand command) {
    var tenant = tenants.create(command.actor().userId(), command.code(), command.name());
    tenants.initializeOwner(command.actor().userId(), tenant.id());
    return tenant;
  }
}
