package com.vishop.iam.modules.tenant.application.command.tenant;

import com.vishop.iam.modules.tenant.application.api.TenantApi;
import com.vishop.iam.modules.tenant.application.dto.TenantResponseDto;
import io.github.coreplatform.common.domain.cqrs.CommandHandler;
import io.github.coreplatform.common.security.constants.CommonPermissions;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class RenameTenantCommandHandler
    implements CommandHandler<RenameTenantCommand, TenantResponseDto> {
  private final TenantApi tenants;
  private final TenantApi authorization;

  public RenameTenantCommandHandler(TenantApi tenants, TenantApi authorization) {
    this.tenants = tenants;
    this.authorization = authorization;
  }

  @Override
  @Transactional
  public TenantResponseDto handle(RenameTenantCommand command) {
    var actor = command.actor();
    var id = command.id();
    var name = command.name();

    authorization.requireAccess(actor, id, CommonPermissions.Tenant.MANAGE);
    tenants.lockTenant(id);
    return tenants.rename(id, name, actor.userId());
  }
}
