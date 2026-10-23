package com.vishop.iam.tenant.application.command;

import com.vishop.iam.access.application.api.AccessApi;
import com.vishop.iam.identity.application.api.IdentityApi;
import com.vishop.iam.shared.application.port.IamEventPublisherPort;
import com.vishop.iam.tenant.application.dto.TenantResponseDto;
import com.vishop.iam.tenant.domain.entities.Tenant;
import com.vishop.iam.tenant.domain.ports.TenantRepositoryPort;
import com.vishop.iam.tenant.domain.valueobjects.TenantCode;
import io.github.coreplatform.common.domain.cqrs.CommandHandler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class CreateTenantCommandHandler
    implements CommandHandler<CreateTenantCommand, TenantResponseDto> {
  private final TenantRepositoryPort tenants;
  private final IdentityApi identities;
  private final AccessApi access;
  private final IamEventPublisherPort outbox;

  public CreateTenantCommandHandler(
      TenantRepositoryPort tenants,
      IdentityApi identities,
      AccessApi access,
      IamEventPublisherPort outbox) {
    this.tenants = tenants;
    this.identities = identities;
    this.access = access;
    this.outbox = outbox;
  }

  @Override
  @Transactional
  public TenantResponseDto handle(CreateTenantCommand command) {
    var actor = command.actor();
    identities.requireActive(actor.userId());
    Tenant tenant = Tenant.create(new TenantCode(command.code()), command.name(), actor.userId());
    tenants.save(tenant);
    access.initializeOwner(actor.userId(), tenant.id());
    tenant.pullDomainEvents().forEach(outbox::publish);
    return TenantResponseDto.from(tenant);
  }
}
