package com.vishop.iam.modules.tenant.domain;

import static org.assertj.core.api.Assertions.*;

import com.vishop.iam.modules.tenant.domain.entity.Tenant;
import com.vishop.iam.modules.tenant.domain.event.TenantCreatedEvent;
import com.vishop.iam.modules.tenant.domain.event.TenantStatusChangedEvent;
import com.vishop.iam.modules.tenant.domain.valueobject.TenantCode;
import com.vishop.iam.modules.tenant.domain.valueobject.TenantStatus;
import io.github.coreplatform.common.domain.exception.BadRequestException;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TenantTest {
  @Test
  void creation_and_reconstitution_have_different_event_semantics() {
    UUID owner = UUID.randomUUID();
    Tenant tenant = Tenant.create(new TenantCode("tenant-one"), "  Shop  ", owner);
    assertThat(tenant.name()).isEqualTo("Shop");
    assertThat(tenant.status()).isEqualTo(TenantStatus.ACTIVE);
    assertThat(tenant.pullDomainEvents())
        .singleElement()
        .isInstanceOfSatisfying(
            TenantCreatedEvent.class,
            event -> {
              assertThat(event.tenantId()).isEqualTo(tenant.id());
              assertThat(event.ownerId()).isEqualTo(owner);
              assertThat(event.code()).isEqualTo("tenant-one");
            });
    assertThat(tenant.pullDomainEvents()).isEmpty();
    Tenant loaded =
        Tenant.reconstitute(
            tenant.id(), tenant.code(), tenant.name(), tenant.status(), tenant.createdAt(), 4L);
    assertThat(loaded.getDomainEvents()).isEmpty();
    assertThat(loaded.getVersion()).isEqualTo(4L);
  }

  @Test
  void archived_tenant_cannot_be_reactivated() {
    UUID actor = UUID.randomUUID();
    Tenant tenant =
        Tenant.reconstitute(
            UUID.randomUUID(),
            new TenantCode("archived-shop"),
            "Shop",
            TenantStatus.SUSPENDED,
            Instant.now(),
            1L);
    tenant.changeStatus(TenantStatus.ARCHIVED, actor);
    assertThat(tenant.pullDomainEvents())
        .singleElement()
        .isInstanceOf(TenantStatusChangedEvent.class);
    assertThatThrownBy(() -> tenant.changeStatus(TenantStatus.ACTIVE, actor))
        .isInstanceOf(BadRequestException.class);
    assertThat(tenant.status()).isEqualTo(TenantStatus.ARCHIVED);
    assertThat(tenant.getDomainEvents()).isEmpty();
  }

  @Test
  void invalid_values_cannot_bypass_http_validation() {
    assertThatThrownBy(() -> new TenantCode(null)).isInstanceOf(BadRequestException.class);
    assertThatThrownBy(() -> new TenantCode("BAD CODE")).isInstanceOf(BadRequestException.class);
    Tenant tenant = Tenant.create(new TenantCode("valid-shop"), "Shop", UUID.randomUUID());
    tenant.clearDomainEvents();
    assertThatThrownBy(() -> tenant.rename(" ", UUID.randomUUID()))
        .isInstanceOf(BadRequestException.class);
    assertThat(tenant.name()).isEqualTo("Shop");
    assertThat(tenant.getDomainEvents()).isEmpty();
  }
}
