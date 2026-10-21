package com.vishop.tenant.application.command;

import io.github.coreplatform.common.domain.cqrs.CommandHandler;
import io.github.coreplatform.common.domain.exception.ConflictException;
import io.github.coreplatform.common.persistence.OutboxService;
import com.vishop.tenant.application.dto.TenantResponseDto;
import com.vishop.tenant.domain.Tenant;
import com.vishop.tenant.domain.TenantCode;
import com.vishop.tenant.domain.TenantRepositoryPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CreateTenantCommandHandler implements CommandHandler<CreateTenantCommand, TenantResponseDto> {

    private final TenantRepositoryPort tenantRepository;
    private final OutboxService outboxService;

    public CreateTenantCommandHandler(TenantRepositoryPort tenantRepository, OutboxService outboxService) {
        this.tenantRepository = tenantRepository;
        this.outboxService = outboxService;
    }

    @Override
    @Transactional
    public TenantResponseDto handle(CreateTenantCommand command) {
        TenantCode code = TenantCode.of(command.code());
        if (tenantRepository.existsByCode(code.value())) {
            throw new ConflictException("tenant_code_already_exists",
                    "A tenant with code '" + code.value() + "' already exists");
        }

        Tenant tenant = Tenant.create(code, command.name());
        tenant = tenantRepository.save(tenant);

        // Store pending domain events into the transactional outbox table in the SAME transaction!
        outboxService.saveEventsFor(tenant, "tenant-service");

        return TenantResponseDto.from(tenant);
    }
}
