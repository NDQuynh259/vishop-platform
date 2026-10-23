package com.vishop.tenant.application.query;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.vishop.tenant.application.dto.TenantFilter;
import com.vishop.tenant.application.dto.TenantResponseDto;
import com.vishop.tenant.domain.entities.Tenant;
import com.vishop.tenant.domain.valueobjects.TenantCode;
import com.vishop.tenant.domain.ports.TenantRepositoryPort;
import com.vishop.tenant.domain.valueobjects.TenantStatus;
import io.github.coreplatform.common.domain.pagination.CursorPagination;
import io.github.coreplatform.common.domain.pagination.CursorParams;
import io.github.coreplatform.common.domain.pagination.OffsetPagination;
import io.github.coreplatform.common.domain.pagination.PagedResult;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ListTenantsQueryHandlerTest {

    private TenantRepositoryPort tenantRepository;
    private ListTenantsQueryHandler handler;

    @BeforeEach
    void setUp() {
        tenantRepository = mock(TenantRepositoryPort.class);
        handler = new ListTenantsQueryHandler(tenantRepository);
    }

    // #region testCursorBranch
    @Test
    void shouldExecuteCursorPaginationWhenCursorIsProvided() {
        Tenant tenant1 = Tenant.create(TenantCode.of("shop-1"), "Shop 1");
        String cursorToken = CursorPagination.of(Instant.now(), UUID.randomUUID()).encode();

        when(tenantRepository.findTenants(eq(TenantStatus.ACTIVE), eq("shop"), any(), any(), eq(11)))
                .thenReturn(List.of(tenant1));

        ListTenantsQuery query = ListTenantsQuery.ofCursor(
                new TenantFilter("shop", TenantStatus.ACTIVE),
                CursorParams.of(cursorToken, 10));

        assertTrue(query.isCursor());
        PagedResult<TenantResponseDto> result = handler.handle(query);

        assertNotNull(result);
        assertEquals(1, result.items().size());
        assertEquals("shop-1", result.items().get(0).code());
        assertNull(result.page()); // cursor pagination has no page number
        assertNull(result.total()); // cursor pagination has no total count
        verify(tenantRepository).findTenants(eq(TenantStatus.ACTIVE), eq("shop"), any(), any(), eq(11));
    }
    // #endregion

    // #region testOffsetBranch
    @Test
    void shouldExecuteOffsetPaginationWhenCursorIsAbsent() {
        Tenant tenant1 = Tenant.create(TenantCode.of("shop-2"), "Shop 2");

        when(tenantRepository.findTenantsOffset(eq(TenantStatus.ACTIVE), eq("shop"), any(OffsetPagination.class)))
                .thenReturn(PagedResult.ofOffset(List.of(tenant1), 2, 10, 25L));

        ListTenantsQuery query = ListTenantsQuery.ofOffset(
                new TenantFilter("shop", TenantStatus.ACTIVE),
                OffsetPagination.of(2, 10));

        assertFalse(query.isCursor());
        PagedResult<TenantResponseDto> result = handler.handle(query);

        assertNotNull(result);
        assertEquals(1, result.items().size());
        assertEquals(2, result.page());
        assertEquals(10, result.limit());
        assertEquals(25L, result.total());
        assertEquals(3, result.totalPages());
        verify(tenantRepository).findTenantsOffset(eq(TenantStatus.ACTIVE), eq("shop"), any(OffsetPagination.class));
    }
    // #endregion
}
