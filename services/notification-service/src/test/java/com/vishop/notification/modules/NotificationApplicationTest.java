package com.vishop.notification.modules;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.vishop.notification.composition.publication.NotificationSystemTypePolicy;
import com.vishop.notification.modules.announcement.application.api.AnnouncementApi;
import com.vishop.notification.modules.announcement.application.service.AnnouncementApplicationService;
import com.vishop.notification.modules.announcement.domain.entity.Announcement;
import com.vishop.notification.modules.announcement.domain.port.AnnouncementRepository;
import com.vishop.notification.modules.configuration.application.api.ConfigurationApi;
import com.vishop.notification.modules.configuration.application.service.ConfigurationApplicationService;
import com.vishop.notification.modules.configuration.domain.entity.TenantConfiguration;
import com.vishop.notification.modules.configuration.domain.entity.UserPreference;
import com.vishop.notification.modules.configuration.domain.port.NotificationConfigurationRepository;
import com.vishop.notification.modules.delivery.application.api.DeliveryApi;
import com.vishop.notification.modules.delivery.application.service.DeliveryApplicationService;
import com.vishop.notification.modules.delivery.domain.port.NotificationDeliveryRepository;
import com.vishop.notification.shared.contract.NotificationChannel;
import com.vishop.notification.shared.contract.NotificationMessage;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class NotificationApplicationTest {
  static final Instant NOW = Instant.parse("2026-11-04T00:00:00Z");
  static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

  @Test
  void configurationGatesUnavailableTypesAndCannotBypassUserOptOut() {
    var repo = mock(NotificationConfigurationRepository.class);
    var service = new ConfigurationApplicationService(repo);
    UUID tenant = UUID.randomUUID(), user = UUID.randomUUID();
    var unavailable =
        new ConfigurationApi.TypePolicy(
            "STOCK_LOW", "INVENTORY", false, true, Set.of(NotificationChannel.IN_APP));
    assertThat(service.resolve(tenant, unavailable).channels()).isEmpty();
    verifyNoInteractions(repo);
    when(repo.loadTenant(tenant, "STOCK_LOW", "INVENTORY"))
        .thenReturn(TenantConfiguration.defaults());
    when(repo.findPreference(tenant, user, "STOCK_LOW"))
        .thenReturn(Optional.of(new UserPreference(false, Set.of(NotificationChannel.IN_APP))));
    var available =
        new ConfigurationApi.TypePolicy(
            "STOCK_LOW",
            "INVENTORY",
            true,
            true,
            Set.of(NotificationChannel.IN_APP, NotificationChannel.EMAIL));
    assertThat(service.resolve(tenant, available).channels())
        .containsExactly(NotificationChannel.IN_APP);
    assertThat(
            service.restrictForUser(tenant, user, "STOCK_LOW", Set.of(NotificationChannel.IN_APP)))
        .isEmpty();
  }

  @Test
  void deliveryCollapsesSameDestinationAndRejectsUnsupportedChannelBeforeAppend() {
    var repo = mock(NotificationDeliveryRepository.class);
    var service =
        new DeliveryApplicationService(
            repo, CLOCK, UUID::randomUUID, Set.of(NotificationChannel.EMAIL));
    UUID tenant = UUID.randomUUID(), notification = UUID.randomUUID();
    var jobs =
        service.enqueue(
            tenant,
            notification,
            List.of(
                new DeliveryApi.Target(
                    UUID.randomUUID(), NotificationChannel.EMAIL, " User@example.test "),
                new DeliveryApi.Target(
                    UUID.randomUUID(), NotificationChannel.EMAIL, "user@EXAMPLE.test")));
    assertThat(jobs).hasSize(1);
    verify(repo)
        .append(
            argThat(
                values ->
                    values.size() == 1
                        && values.getFirst().destination().equals("User@example.test")));
    clearInvocations(repo);
    assertThatThrownBy(
            () ->
                service.enqueue(
                    tenant,
                    notification,
                    List.of(new DeliveryApi.Target(null, NotificationChannel.TELEGRAM, "-100123"))))
        .isInstanceOf(IllegalArgumentException.class);
    verifyNoInteractions(repo);
  }

  @Test
  void cooldownAdapterCannotAddRecipientsOrChannels() {
    var repo = mock(NotificationDeliveryRepository.class);
    var service =
        new DeliveryApplicationService(
            repo, CLOCK, UUID::randomUUID, Set.of(NotificationChannel.EMAIL));
    UUID user = UUID.randomUUID();
    var message = message();
    var input = Map.of(user, Set.of(NotificationChannel.IN_APP));
    when(repo.reserveAllowedRoutes(message, input, NOW))
        .thenReturn(Map.of(user, Set.of(NotificationChannel.IN_APP, NotificationChannel.EMAIL)));
    assertThatThrownBy(() -> service.reserveAllowedRoutes(message, input))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void announcementChecksTypeConstantsAndUsesCompareAndSwapVersion() {
    var policy = new NotificationSystemTypePolicy();
    assertThatThrownBy(() -> policy.requirePublishableSystemType("STOCK_LOW"))
        .isInstanceOf(IllegalArgumentException.class);
    var repo = mock(AnnouncementRepository.class);
    UUID id = UUID.randomUUID();
    var audience =
        new Announcement.Audience(Announcement.AudienceMode.ALL_ACTIVE_TENANTS, Set.of());
    var draft =
        new Announcement(
            id,
            "SYSTEM_MAINTENANCE",
            "Title",
            "Body",
            audience,
            Set.of(NotificationChannel.IN_APP),
            0,
            null);
    when(repo.find(id)).thenReturn(Optional.of(draft));
    assertThatThrownBy(() -> policy.requirePublishableSystemType("CUSTOM_LEGACY_TYPE"))
        .isInstanceOf(IllegalArgumentException.class);
    var service = new AnnouncementApplicationService(repo, policy, CLOCK, UUID::randomUUID);
    var published = service.publish(id, 0);
    assertThat(published.publishedAt()).isEqualTo(NOW);
    verify(repo)
        .save(argThat(value -> value.publishedAt().equals(NOW) && value.version() == 1), eq(0L));
    assertThatThrownBy(() -> service.publish(id, 2)).isInstanceOf(IllegalStateException.class);
    assertThat(published.audience().mode())
        .isEqualTo(AnnouncementApi.AudienceMode.ALL_ACTIVE_TENANTS);
  }

  static NotificationMessage message() {
    return new NotificationMessage(
        UUID.randomUUID(),
        "event-1",
        "STOCK_LOW",
        "SKU",
        "sku-1",
        "inventory.view",
        null,
        NotificationMessage.Severity.WARNING,
        "Low stock",
        "Body",
        null,
        NOW);
  }
}
