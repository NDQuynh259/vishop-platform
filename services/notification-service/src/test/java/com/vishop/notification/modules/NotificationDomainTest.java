package com.vishop.notification.modules;

import static org.assertj.core.api.Assertions.*;

import com.vishop.notification.modules.announcement.domain.entity.Announcement;
import com.vishop.notification.modules.configuration.domain.entity.TenantConfiguration;
import com.vishop.notification.modules.configuration.domain.entity.UserPreference;
import com.vishop.notification.modules.delivery.domain.entity.DeliveryJob;
import com.vishop.notification.modules.inbox.domain.entity.NotificationRecipient;
import com.vishop.notification.shared.contract.NotificationChannel;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class NotificationDomainTest {
  static final Instant NOW = Instant.parse("2026-11-04T00:00:00Z");

  @Test
  void tenantAndPersonalSwitchesCanOnlyRestrictChannels() {
    var all =
        Set.of(NotificationChannel.IN_APP, NotificationChannel.EMAIL, NotificationChannel.TELEGRAM);
    var tenant =
        new TenantConfiguration(
            true,
            Set.of(NotificationChannel.IN_APP),
            Optional.of(new TenantConfiguration.TypeRule(true, all, Set.of())));
    var allowed = tenant.resolve(true, all);
    assertThat(allowed).containsExactly(NotificationChannel.IN_APP);
    assertThat(new UserPreference(true, all).restrict(allowed))
        .containsExactly(NotificationChannel.IN_APP);
    assertThat(new UserPreference(false, all).restrict(allowed)).isEmpty();
    assertThat(new TenantConfiguration(false, all, Optional.empty()).resolve(true, all)).isEmpty();
    assertThat(
            new TenantConfiguration(
                    true, all, Optional.of(new TenantConfiguration.TypeRule(false, all, Set.of())))
                .resolve(true, all))
        .isEmpty();
  }

  @Test
  void missingTenantConfigurationUsesInAppDefaultAndCatalogDefaultCanDisable() {
    assertThat(
            TenantConfiguration.defaults()
                .resolve(true, Set.of(NotificationChannel.IN_APP, NotificationChannel.EMAIL)))
        .containsExactly(NotificationChannel.IN_APP);
    assertThat(TenantConfiguration.defaults().resolve(false, Set.of(NotificationChannel.IN_APP)))
        .isEmpty();
  }

  @Test
  void recipientReadIsIdempotentAndIndependentAndArchiveDoesNotChangeContent() {
    UUID tenant = UUID.randomUUID(), notification = UUID.randomUUID();
    var first = new NotificationRecipient(tenant, notification, UUID.randomUUID(), NOW, null, null);
    var second =
        new NotificationRecipient(tenant, notification, UUID.randomUUID(), NOW, null, null);
    var read = first.markRead(NOW.plusSeconds(1));
    assertThat(read.markRead(NOW.plusSeconds(2))).isSameAs(read);
    assertThat(second.readAt()).isNull();
    assertThat(read.archive(NOW.plusSeconds(3)).readAt()).isEqualTo(NOW.plusSeconds(1));
    assertThatThrownBy(() -> first.markRead(NOW.minusSeconds(1)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> first.archive(NOW).markRead(NOW))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void deliveryCompletionRequiresCurrentUnexpiredLease() {
    var pending = pending();
    UUID token = UUID.randomUUID();
    var processing = pending.claim(token, NOW, Duration.ofSeconds(30));
    assertThat(processing.attemptCount()).isEqualTo(1);
    assertThatThrownBy(() -> processing.complete(UUID.randomUUID(), NOW))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> processing.complete(token, NOW.plusSeconds(30)))
        .isInstanceOf(IllegalStateException.class);
    var sent = processing.complete(token, NOW.plusSeconds(1));
    assertThat(sent.status()).isEqualTo(DeliveryJob.Status.SENT);
    assertThat(sent.lockToken()).isNull();
    assertThatThrownBy(() -> sent.claim(token, NOW.plusSeconds(2), Duration.ofSeconds(30)))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void retryHonorsDueTimeAndStopsAtBudget() {
    DeliveryJob job = pending();
    Instant time = NOW;
    for (int attempt = 1; attempt <= 5; attempt++) {
      UUID token = UUID.randomUUID();
      job = job.claim(token, time, Duration.ofSeconds(30));
      job = job.fail(token, time.plusSeconds(1), time.plusSeconds(10));
      time = time.plusSeconds(10);
    }
    assertThat(job.status()).isEqualTo(DeliveryJob.Status.FAILED);
    assertThat(job.attemptCount()).isEqualTo(5);
    var failed = job;
    assertThatThrownBy(
            () -> failed.claim(UUID.randomUUID(), NOW.plusSeconds(100), Duration.ofSeconds(30)))
        .isInstanceOf(IllegalStateException.class);
    var retry = pending().claim(UUID.randomUUID(), NOW, Duration.ofSeconds(30));
    var token = retry.lockToken();
    var waiting = retry.fail(token, NOW.plusSeconds(1), NOW.plusSeconds(10));
    assertThatThrownBy(
            () -> waiting.claim(UUID.randomUUID(), NOW.plusSeconds(2), Duration.ofSeconds(30)))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void publishedAnnouncementCannotBeRevisedOrPublishedAgain() {
    var audience =
        new Announcement.Audience(
            Announcement.AudienceMode.SELECTED_TENANTS, Set.of(UUID.randomUUID()));
    var draft =
        new Announcement(
            UUID.randomUUID(),
            "SYSTEM_MAINTENANCE",
            "Maintenance",
            "Body",
            audience,
            Set.of(NotificationChannel.IN_APP),
            0,
            null);
    var published = draft.publish(NOW);
    assertThat(published.version()).isEqualTo(1);
    assertThatThrownBy(() -> published.revise("Changed", "Body", audience, draft.channels()))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> published.publish(NOW)).isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(
            () -> new Announcement.Audience(Announcement.AudienceMode.SELECTED_TENANTS, Set.of()))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                new Announcement.Audience(
                    Announcement.AudienceMode.ALL_ACTIVE_TENANTS, Set.of(UUID.randomUUID())))
        .isInstanceOf(IllegalArgumentException.class);
  }

  private static DeliveryJob pending() {
    return DeliveryJob.pending(
        UUID.randomUUID(),
        UUID.randomUUID(),
        UUID.randomUUID(),
        UUID.randomUUID(),
        NotificationChannel.EMAIL,
        "person@example.test",
        NOW);
  }
}
