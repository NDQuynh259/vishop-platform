package com.vishop.iam.modules.tenant.application.command;

import com.vishop.iam.modules.access.application.api.AccessApi;
import com.vishop.iam.modules.tenant.application.dto.TenantResponseDto;
import com.vishop.iam.modules.tenant.application.mapper.TenantResponseMapper;
import com.vishop.iam.modules.tenant.domain.entity.Tenant;
import com.vishop.iam.modules.tenant.domain.port.TenantRepositoryPort;
import com.vishop.iam.shared.application.port.IamEventPublisherPort;
import io.github.coreplatform.common.domain.cqrs.CommandHandler;
import io.github.coreplatform.common.security.constants.CommonPermissions;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class RenameTenantCommandHandler
    implements CommandHandler<RenameTenantCommand, TenantResponseDto> {
  private final TenantRepositoryPort tenantRepository;
  private final IamEventPublisherPort outbox;
  private final AccessApi authorization;

  public RenameTenantCommandHandler(
      TenantRepositoryPort tenantRepository,
      IamEventPublisherPort outbox,
      AccessApi authorization) {
    this.tenantRepository = tenantRepository;
    this.outbox = outbox;
    this.authorization = authorization;
  }

  @Override
  @Transactional
  public TenantResponseDto handle(RenameTenantCommand command) {
    var actor = command.actor();
    var id = command.id();
    var name = command.name();

    tenantRepository.lockTenant(id);
    authorization.requireAccess(actor.userId(), id, CommonPermissions.Tenant.MANAGE);
    Tenant tenant = tenantRepository.tenant(id);
    tenant.rename(name, actor.userId());
    tenantRepository.save(tenant);
    tenant.pullDomainEvents().forEach(outbox::publish);
    return TenantResponseMapper.from(tenant);
  }
}
