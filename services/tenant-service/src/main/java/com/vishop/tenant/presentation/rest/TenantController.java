package com.vishop.tenant.presentation.rest;

import io.github.coreplatform.common.domain.cqrs.CommandBus;
import io.github.coreplatform.common.domain.cqrs.QueryBus;
import io.github.coreplatform.common.domain.pagination.PagedResult;
import io.github.coreplatform.common.web.pagination.ListEnvelope;
import com.vishop.tenant.application.command.ChangeTenantStatusCommand;
import com.vishop.tenant.application.command.CreateTenantCommand;
import com.vishop.tenant.application.dto.ChangeTenantStatusRequest;
import com.vishop.tenant.application.dto.CreateTenantRequest;
import com.vishop.tenant.application.dto.TenantResponseDto;
import com.vishop.tenant.application.query.GetTenantByIdQuery;
import com.vishop.tenant.application.query.ListTenantsQuery;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for Tenant operations.
 * Communicates strictly with Application Layer via CommandBus and QueryBus.
 */
@RestController
@RequestMapping("/api/v1/tenants")
public class TenantController {

    private final CommandBus commandBus;
    private final QueryBus queryBus;

    // #region TenantController
    public TenantController(CommandBus commandBus, QueryBus queryBus) {
        this.commandBus = commandBus;
        this.queryBus = queryBus;
    }
    // #endregion

    // #region createTenant
    @PostMapping
    public ResponseEntity<TenantResponseDto> createTenant(@Valid @RequestBody CreateTenantRequest request) {
        CreateTenantCommand command = new CreateTenantCommand(request.code(), request.name());
        TenantResponseDto response = commandBus.dispatch(command);
        return ResponseEntity.created(URI.create("/api/v1/tenants/" + response.id())).body(response);
    }
    // #endregion

    // #region getTenantById
    @GetMapping("/{id}")
    public ResponseEntity<TenantResponseDto> getTenantById(@PathVariable("id") UUID id) {
        GetTenantByIdQuery query = new GetTenantByIdQuery(id);
        TenantResponseDto response = queryBus.dispatch(query);
        return ResponseEntity.ok(response);
    }
    // #endregion

    // #region listTenants
    @GetMapping
    public ResponseEntity<ListEnvelope<TenantResponseDto>> listTenants(
            @RequestParam(name = "limit", defaultValue = "20") int limit,
            @RequestParam(name = "cursor", required = false) String cursor,
            HttpServletRequest request) {

        ListTenantsQuery query = new ListTenantsQuery(limit, cursor);
        PagedResult<TenantResponseDto> response = queryBus.dispatch(query);
        return ResponseEntity.ok(ListEnvelope.from(request.getRequestURI(), response));
    }
    // #endregion

    // #region changeStatus
    @PatchMapping("/{id}/status")
    public ResponseEntity<TenantResponseDto> changeStatus(
            @PathVariable("id") UUID id,
            @Valid @RequestBody ChangeTenantStatusRequest request) {

        ChangeTenantStatusCommand command = new ChangeTenantStatusCommand(id, request.status());
        TenantResponseDto response = commandBus.dispatch(command);
        return ResponseEntity.ok(response);
    }
    // #endregion
}
