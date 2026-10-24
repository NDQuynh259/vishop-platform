package com.vishop.iam.modules.tenant.application.command;

import com.vishop.iam.contracts.identity.IdentityApi;
import com.vishop.iam.contracts.tenant.TenantResponseDto;
import com.vishop.iam.modules.tenant.application.mapper.TenantResponseMapper;
import com.vishop.iam.modules.tenant.domain.entity.Tenant;
import com.vishop.iam.modules.tenant.domain.port.TenantRepositoryPort;
import com.vishop.iam.shared.application.port.IamEventPublisherPort;
import io.github.coreplatform.common.domain.cqrs.CommandHandler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class ChangeTenantStatusCommandHandler
    implements CommandHandler<ChangeTenantStatusCommand, TenantResponseDto> {
  private final TenantRepositoryPort tenantRepository;
  private final IamEventPublisherPort outbox;
  private final IdentityApi authorization;

  public ChangeTenantStatusCommandHandler(
      TenantRepositoryPort tenantRepository,
      IamEventPublisherPort outbox,
      IdentityApi authorization) {
    this.tenantRepository = tenantRepository;
    this.outbox = outbox;
    this.authorization = authorization;
  }

  @Override
  @Transactional
  public TenantResponseDto handle(ChangeTenantStatusCommand command) {
    var actor = command.actor();
    var tenant = command.tenant();
    var status = command.status();

    authorization.requireCms(actor);
    tenantRepository.lockTenant(tenant);
    Tenant current = tenantRepository.tenant(tenant);
    current.changeStatus(status, actor.userId());
    tenantRepository.save(current);
    current.pullDomainEvents().forEach(outbox::publish);
    return TenantResponseMapper.from(current);
  }
}
