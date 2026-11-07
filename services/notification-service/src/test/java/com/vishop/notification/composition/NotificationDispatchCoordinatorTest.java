package com.vishop.notification.composition;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.vishop.notification.composition.dispatch.DispatchJournal;
import com.vishop.notification.composition.dispatch.NotificationDispatchCoordinator;
import com.vishop.notification.composition.dispatch.NotificationDispatchPolicy;
import com.vishop.notification.composition.dispatch.RecipientDirectory;
import com.vishop.notification.modules.delivery.application.api.NotificationDeliveryApi;
import com.vishop.notification.modules.delivery.application.command.EnqueueDeliveriesCommandHandler;
import com.vishop.notification.modules.delivery.application.command.ReserveRoutesCommandHandler;
import com.vishop.notification.modules.inbox.application.command.CreateNotificationCommandHandler;
import com.vishop.notification.modules.inbox.application.service.NotificationInboxApplicationService;
import com.vishop.notification.shared.contract.NotificationChannel;
import com.vishop.notification.shared.contract.NotificationMessage;
import io.github.coreplatform.common.domain.cqrs.CommandBus;
import io.github.coreplatform.common.web.cqrs.CqrsAutoConfiguration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

class NotificationDispatchCoordinatorTest {
  final NotificationDispatchPolicy setting = mock(NotificationDispatchPolicy.class);
  final NotificationInboxApplicationService inbox = mock(NotificationInboxApplicationService.class);
  final NotificationDeliveryApi delivery = mock(NotificationDeliveryApi.class);
  final RecipientDirectory directory = mock(RecipientDirectory.class);
  final TestJournal journal = new TestJournal();
  NotificationDispatchCoordinator coordinator;
  AnnotationConfigApplicationContext context;

  @BeforeEach
  void setupCqrs() {
    context = new AnnotationConfigApplicationContext();
    context.registerBean(NotificationInboxApplicationService.class, () -> inbox);
    context.registerBean(NotificationDeliveryApi.class, () -> delivery);
    context.register(
        CqrsAutoConfiguration.class,
        CreateNotificationCommandHandler.class,
        ReserveRoutesCommandHandler.class,
        EnqueueDeliveriesCommandHandler.class);
    context.refresh();
    coordinator =
        new NotificationDispatchCoordinator(
            setting, context.getBean(CommandBus.class), directory, journal);
  }

  @AfterEach
  void closeCqrs() {
    context.close();
  }

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
    when(setting.resolve(eq(message.tenantId()), any()))
        .thenReturn(new NotificationDispatchPolicy.RoutingPolicy(channels, Set.of(selected)));
    when(directory.eligible(message, Set.of(selected))).thenReturn(Set.of(selected, extra));
    when(setting.restrictForUser(message.tenantId(), selected, message.type(), channels))
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
    verify(setting, never())
        .restrictForUser(eq(message.tenantId()), eq(extra), anyString(), anySet());
    verify(delivery, times(1)).reserveAllowedRoutes(message, routes);
    verify(setting)
        .resolve(
            message.tenantId(),
            new NotificationDispatchPolicy.TypePolicy(
                "STOCK_LOW", "INVENTORY", true, true, channels));
  }

  @Test
  void disabledTenantPolicyCreatesNoRowsOrRealtimeSignal() {
    when(setting.resolve(eq(message.tenantId()), any()))
        .thenReturn(new NotificationDispatchPolicy.RoutingPolicy(Set.of(), Set.of()));
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
    verifyNoInteractions(setting, inbox, delivery, directory);
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
