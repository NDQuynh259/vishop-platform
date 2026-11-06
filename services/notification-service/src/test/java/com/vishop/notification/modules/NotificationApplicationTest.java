package com.vishop.notification.modules;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.vishop.notification.composition.publication.NotificationSystemTypePolicy;
import com.vishop.notification.modules.announcement.application.api.NotificationAnnouncementApi;
import com.vishop.notification.modules.announcement.application.service.NotificationAnnouncementApplicationService;
import com.vishop.notification.modules.announcement.domain.entity.NotificationAnnouncement;
import com.vishop.notification.modules.announcement.domain.port.NotificationAnnouncementRepository;
import com.vishop.notification.modules.delivery.application.api.NotificationDeliveryApi;
import com.vishop.notification.modules.delivery.application.service.NotificationDeliveryApplicationService;
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
  void deliveryCollapsesSameDestinationAndRejectsUnsupportedChannelBeforeAppend() {
    var repo = mock(NotificationDeliveryRepository.class);
    var service =
        new NotificationDeliveryApplicationService(
            repo, CLOCK, UUID::randomUUID, Set.of(NotificationChannel.EMAIL));
    UUID tenant = UUID.randomUUID(), notification = UUID.randomUUID();
    var jobs =
        service.enqueue(
            tenant,
            notification,
            List.of(
                new NotificationDeliveryApi.Target(
                    UUID.randomUUID(), NotificationChannel.EMAIL, " User@example.test "),
                new NotificationDeliveryApi.Target(
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
                    List.of(
                        new NotificationDeliveryApi.Target(
                            null, NotificationChannel.TELEGRAM, "-100123"))))
        .isInstanceOf(IllegalArgumentException.class);
    verifyNoInteractions(repo);
  }

  @Test
  void cooldownAdapterCannotAddRecipientsOrChannels() {
    var repo = mock(NotificationDeliveryRepository.class);
    var service =
        new NotificationDeliveryApplicationService(
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
    var repo = mock(NotificationAnnouncementRepository.class);
    UUID id = UUID.randomUUID();
    var audience =
        new NotificationAnnouncement.Audience(
            NotificationAnnouncement.AudienceMode.ALL_ACTIVE_TENANTS, Set.of());
    var draft =
        new NotificationAnnouncement(
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
    var service =
        new NotificationAnnouncementApplicationService(repo, policy, CLOCK, UUID::randomUUID);
    var published = service.publish(id, 0);
    assertThat(published.publishedAt()).isEqualTo(NOW);
    verify(repo)
        .save(argThat(value -> value.publishedAt().equals(NOW) && value.version() == 1), eq(0L));
    assertThatThrownBy(() -> service.publish(id, 2)).isInstanceOf(IllegalStateException.class);
    assertThat(published.audience().mode())
        .isEqualTo(NotificationAnnouncementApi.AudienceMode.ALL_ACTIVE_TENANTS);
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
