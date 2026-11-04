package com.vishop.notification.composition;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.vishop.notification.composition.dispatch.DispatchJournal;
import com.vishop.notification.composition.dispatch.NotificationDispatchCoordinator;
import com.vishop.notification.composition.dispatch.RecipientDirectory;
import com.vishop.notification.modules.configuration.application.api.ConfigurationApi;
import com.vishop.notification.modules.delivery.application.api.DeliveryApi;
import com.vishop.notification.modules.inbox.application.api.InboxApi;
import com.vishop.notification.shared.contract.NotificationChannel;
import com.vishop.notification.shared.contract.NotificationMessage;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

class NotificationDispatchCoordinatorTest {
  final ConfigurationApi configuration = mock(ConfigurationApi.class);
  final InboxApi inbox = mock(InboxApi.class);
  final DeliveryApi delivery = mock(DeliveryApi.class);
  final RecipientDirectory directory = mock(RecipientDirectory.class);
  final TestJournal journal = new TestJournal();
  final NotificationDispatchCoordinator coordinator =
      new NotificationDispatchCoordinator(configuration, inbox, delivery, directory, journal);
  final NotificationMessage message =
      new NotificationMessage(
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
          Instant.parse("2026-11-04T00:00:00Z"));

  @Test
  void replayDoesNotRecreateNotificationAndExplicitSelectionIsEnforced() {
    UUID selected = UUID.randomUUID(), extra = UUID.randomUUID(), id = UUID.randomUUID();
    var channels = Set.of(NotificationChannel.IN_APP);
    var routes = Map.of(selected, channels);
    when(configuration.resolve(eq(message.tenantId()), any()))
        .thenReturn(new ConfigurationApi.RoutingPolicy(channels, Set.of(selected)));
    when(directory.eligible(message, Set.of(selected))).thenReturn(Set.of(selected, extra));
    when(configuration.restrictForUser(message.tenantId(), selected, message.type(), channels))
        .thenReturn(channels);
    when(delivery.reserveAllowedRoutes(message, routes)).thenReturn(routes);
    when(directory.providerTargets(message, routes)).thenReturn(List.of());
    when(inbox.create(message, Set.of(selected))).thenReturn(id);
    when(delivery.enqueue(message.tenantId(), id, List.of())).thenReturn(List.of());
    var result = coordinator.dispatch(message).orElseThrow();
    assertThat(result.notificationId()).contains(id);
    assertThat(result.inAppRecipientCount()).isEqualTo(1);
    assertThat(journal.signalRecipients).isEqualTo(Set.of(selected));
    assertThat(coordinator.dispatch(message)).isEmpty();
    verify(inbox, times(1)).create(message, Set.of(selected));
    verify(configuration, never())
        .restrictForUser(eq(message.tenantId()), eq(extra), anyString(), anySet());
    verify(delivery, times(1)).reserveAllowedRoutes(message, routes);
    verify(configuration)
        .resolve(
            message.tenantId(),
            new ConfigurationApi.TypePolicy("STOCK_LOW", "INVENTORY", true, true, channels));
  }

  @Test
  void disabledTenantPolicyCreatesNoRowsOrRealtimeSignal() {
    when(configuration.resolve(eq(message.tenantId()), any()))
        .thenReturn(new ConfigurationApi.RoutingPolicy(Set.of(), Set.of()));
    var result = coordinator.dispatch(message).orElseThrow();
    assertThat(result.notificationId()).isEmpty();
    verifyNoInteractions(inbox, delivery, directory);
    assertThat(journal.signalRecipients).isEmpty();
  }

  @Test
  void failedDispatchDoesNotConsumeEventInTheJournalContract() {
    var unknown =
        new NotificationMessage(
            message.tenantId(),
            message.sourceEventId(),
            "CUSTOM_LEGACY_TYPE",
            message.subjectType(),
            message.subjectId(),
            message.requiredPermission(),
            message.shopId(),
            message.severity(),
            message.title(),
            message.body(),
            message.actionUrl(),
            message.occurredAt());
    assertThatThrownBy(() -> coordinator.dispatch(unknown))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(journal.consumed).isEmpty();
    verifyNoInteractions(configuration, inbox, delivery, directory);
  }

  /** Test double only: durable rollback and idempotency require the future DB adapter. */
  static final class TestJournal implements DispatchJournal {
    final Set<Key> consumed = new HashSet<>();
    Set<UUID> signalRecipients = Set.of();

    @Override
    public <T> Optional<T> once(Key key, Supplier<T> operation) {
      if (consumed.contains(key)) return Optional.empty();
      T result = operation.get();
      consumed.add(key);
      return Optional.of(result);
    }

    @Override
    public void notificationCreated(UUID tenant, UUID notification, Set<UUID> recipients) {
      signalRecipients = Set.copyOf(recipients);
    }
  }
}
