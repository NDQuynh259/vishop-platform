package com.vishop.iam.tenant.application.command;

import com.vishop.iam.identity.application.api.IdentityApi;
import com.vishop.iam.shared.application.port.IamEventPublisherPort;
import com.vishop.iam.tenant.application.dto.TenantResponseDto;
import com.vishop.iam.tenant.domain.entities.Tenant;
import com.vishop.iam.tenant.domain.ports.TenantRepositoryPort;
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
    return TenantResponseDto.from(current);
  }
}
