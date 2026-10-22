package com.vishop.tenant.presentation.rest;

import io.github.coreplatform.common.domain.cqrs.CommandBus;
import io.github.coreplatform.common.domain.cqrs.QueryBus;
import io.github.coreplatform.common.domain.pagination.PagedResult;
import io.github.coreplatform.common.web.pagination.ListEnvelope;
import io.github.coreplatform.common.web.pagination.PaginationRequest;
import com.vishop.tenant.application.command.ChangeTenantStatusCommand;
import com.vishop.tenant.application.command.CreateTenantCommand;
import com.vishop.tenant.application.dto.ChangeTenantStatusRequest;
import com.vishop.tenant.application.dto.CreateTenantRequest;
import com.vishop.tenant.application.dto.TenantFilter;
import com.vishop.tenant.application.dto.TenantResponseDto;
import com.vishop.tenant.application.query.GetTenantByIdQuery;
import com.vishop.tenant.application.query.ListTenantsQuery;
import com.vishop.tenant.domain.TenantStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springdoc.core.annotations.ParameterObject;
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
@Tag(name = "Tenants", description = "Tenant management and multi-tenancy operations")
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
    @Operation(summary = "Create a new tenant", description = "Creates a new tenant aggregate and emits TenantCreatedEvent.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Tenant created successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid payload or validation failure"),
            @ApiResponse(responseCode = "409", description = "Tenant code already exists")
    })
    @PostMapping
    public ResponseEntity<TenantResponseDto> createTenant(@Valid @RequestBody CreateTenantRequest request) {
        CreateTenantCommand command = new CreateTenantCommand(request.code(), request.name());
        TenantResponseDto response = commandBus.dispatch(command);
        return ResponseEntity.created(URI.create("/api/v1/tenants/" + response.id())).body(response);
    }
    // #endregion

    // #region getTenantById
    @Operation(summary = "Get tenant by ID", description = "Retrieves a single tenant by its unique UUID identifier.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Tenant found"),
            @ApiResponse(responseCode = "404", description = "Tenant not found")
    })
    @GetMapping("/{id}")
    public ResponseEntity<TenantResponseDto> getTenantById(
            @Parameter(description = "Unique tenant UUID", example = "a1b2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d")
            @PathVariable("id") UUID id) {
        GetTenantByIdQuery query = new GetTenantByIdQuery(id);
        TenantResponseDto response = queryBus.dispatch(query);
        return ResponseEntity.ok(response);
    }
    // #endregion

    // #region listTenants
    @Operation(
            summary = "List tenants",
            description = "Retrieves a paginated list of tenants with optional status and search keyword filters. Supports both Keyset Cursor and Offset pagination (prioritizes cursor if provided)."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Paginated tenant list returned successfully")
    })
    @GetMapping
    public ResponseEntity<ListEnvelope<TenantResponseDto>> listTenants(
            @Parameter(description = "Search keyword matched against tenant name and code")
            @RequestParam(name = "search", required = false) String search,
            @Parameter(description = "Filter by tenant status")
            @RequestParam(name = "status", required = false) TenantStatus status,
            @ParameterObject PaginationRequest pagination,
            HttpServletRequest request) {

        TenantFilter filter = new TenantFilter(search, status);
        ListTenantsQuery query = pagination.isCursor()
                ? ListTenantsQuery.ofCursor(filter, pagination.toCursorParams())
                : ListTenantsQuery.ofOffset(filter, pagination.toOffsetPagination());

        PagedResult<TenantResponseDto> response = queryBus.dispatch(query);
        return ResponseEntity.ok(ListEnvelope.from(request.getRequestURI(), response));
    }
    // #endregion

    // #region changeStatus
    @Operation(summary = "Change tenant status", description = "Updates tenant lifecycle status (ACTIVE, SUSPENDED, ARCHIVED).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Tenant status updated successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid status transition"),
            @ApiResponse(responseCode = "404", description = "Tenant not found")
    })
    @PatchMapping("/{id}/status")
    public ResponseEntity<TenantResponseDto> changeStatus(
            @Parameter(description = "Unique tenant UUID", example = "a1b2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d")
            @PathVariable("id") UUID id,
            @Valid @RequestBody ChangeTenantStatusRequest request) {

        ChangeTenantStatusCommand command = new ChangeTenantStatusCommand(id, request.status());
        TenantResponseDto response = commandBus.dispatch(command);
        return ResponseEntity.ok(response);
    }
    // #endregion
}
