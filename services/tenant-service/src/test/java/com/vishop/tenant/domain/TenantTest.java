package com.vishop.tenant.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.coreplatform.common.domain.DomainEvent;
import io.github.coreplatform.common.domain.exception.DomainException;
import com.vishop.tenant.domain.event.TenantCreatedEvent;
import com.vishop.tenant.domain.event.TenantStatusChangedEvent;
import java.util.List;
import org.junit.jupiter.api.Test;

class TenantTest {

    // #region shouldCreateTenantAndEmitCreatedEvent
    @Test
    void shouldCreateTenantAndEmitCreatedEvent() {
        TenantCode code = TenantCode.of("my-shop-vn");
        Tenant tenant = Tenant.create(code, "My Shop Vietnam");

        assertNotNull(tenant.getId());
        assertEquals("my-shop-vn", tenant.getCode().value());
        assertEquals("My Shop Vietnam", tenant.getName());
        assertEquals(TenantStatus.ACTIVE, tenant.getStatus());

        List<DomainEvent> events = tenant.pullDomainEvents();
        assertEquals(1, events.size());
        assertTrueEventIs(events.get(0), TenantCreatedEvent.class);
    }
    // #endregion

    // #region shouldValidateTenantCodeFormat
    @Test
    void shouldValidateTenantCodeFormat() {
        assertThrows(DomainException.class, () -> TenantCode.of(""));
        assertThrows(DomainException.class, () -> TenantCode.of("a"));
        assertThrows(DomainException.class, () -> TenantCode.of("-invalid-start"));
        assertThrows(DomainException.class, () -> TenantCode.of("INVALID_UPPERCASE"));
    }
    // #endregion

    // #region shouldChangeStatusAndEmitStatusChangedEvent
    @Test
    void shouldChangeStatusAndEmitStatusChangedEvent() {
        Tenant tenant = Tenant.create(TenantCode.of("my-store"), "My Store");
        tenant.pullDomainEvents(); // Clear creation event

        tenant.suspend();
        assertEquals(TenantStatus.SUSPENDED, tenant.getStatus());

        List<DomainEvent> events = tenant.pullDomainEvents();
        assertEquals(1, events.size());
        TenantStatusChangedEvent event = (TenantStatusChangedEvent) events.get(0);
        assertEquals(TenantStatus.ACTIVE, event.oldStatus());
        assertEquals(TenantStatus.SUSPENDED, event.newStatus());
    }
    // #endregion

    // #region shouldRejectMutationOnArchivedTenant
    @Test
    void shouldRejectMutationOnArchivedTenant() {
        Tenant tenant = Tenant.create(TenantCode.of("shop-archive"), "Shop Archive");
        tenant.archive();
        assertEquals(TenantStatus.ARCHIVED, tenant.getStatus());

        assertThrows(DomainException.class, () -> tenant.activate());
        assertThrows(DomainException.class, () -> tenant.suspend());
        assertThrows(DomainException.class, () -> tenant.updateName("New Name"));
    }
    // #endregion

    // #region assertTrueEventIs
    private void assertTrueEventIs(DomainEvent event, Class<?> expectedClass) {
        assertEquals(expectedClass, event.getClass());
    }
    // #endregion
}
