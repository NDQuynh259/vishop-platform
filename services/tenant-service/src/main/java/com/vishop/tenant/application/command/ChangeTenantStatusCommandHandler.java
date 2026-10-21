package com.vishop.tenant.application.command;

import io.github.coreplatform.common.domain.cqrs.CommandHandler;
import io.github.coreplatform.common.domain.exception.EntityNotFoundException;
import io.github.coreplatform.common.persistence.OutboxService;
import com.vishop.tenant.application.dto.TenantResponseDto;
import com.vishop.tenant.domain.Tenant;
import com.vishop.tenant.domain.TenantRepositoryPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ChangeTenantStatusCommandHandler implements CommandHandler<ChangeTenantStatusCommand, TenantResponseDto> {

    private final TenantRepositoryPort tenantRepository;
    private final OutboxService outboxService;

    public ChangeTenantStatusCommandHandler(TenantRepositoryPort tenantRepository, OutboxService outboxService) {
        this.tenantRepository = tenantRepository;
        this.outboxService = outboxService;
    }

    @Override
    @Transactional
    public TenantResponseDto handle(ChangeTenantStatusCommand command) {
        Tenant tenant = tenantRepository.findById(command.tenantId())
                .orElseThrow(() -> new EntityNotFoundException("Tenant", command.tenantId()));

        switch (command.newStatus()) {
            case ACTIVE -> tenant.activate();
            case SUSPENDED -> tenant.suspend();
            case ARCHIVED -> tenant.archive();
        }

        tenant = tenantRepository.save(tenant);
        outboxService.saveEventsFor(tenant, "tenant-service");

        return TenantResponseDto.from(tenant);
    }
}
