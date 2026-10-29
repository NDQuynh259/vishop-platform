package com.vishop.iam.modules.tenant.application.command.tenant;

import com.vishop.iam.modules.identity.application.api.IdentityApi;
import com.vishop.iam.modules.tenant.application.api.TenantApi;
import com.vishop.iam.modules.tenant.application.dto.TenantResponseDto;
import io.github.coreplatform.common.domain.cqrs.CommandHandler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class UpdateTenantCommandHandler
    implements CommandHandler<UpdateTenantCommand, TenantResponseDto> {
  private final TenantApi tenants;
  private final IdentityApi authorization;

  public UpdateTenantCommandHandler(TenantApi tenants, IdentityApi authorization) {
    this.tenants = tenants;
    this.authorization = authorization;
  }

  @Override
  @Transactional
  public TenantResponseDto handle(UpdateTenantCommand command) {
    var actor = command.actor();
    var tenant = command.tenant();
    var status = command.status();

    authorization.requireCms(actor);
    tenants.lockTenant(tenant);
    return tenants.update(tenant, command.name(), status, actor.userId());
  }
}
