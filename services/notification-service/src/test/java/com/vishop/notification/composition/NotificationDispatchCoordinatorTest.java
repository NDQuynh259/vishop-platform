package com.vishop.notification.composition;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.vishop.notification.composition.dispatch.DispatchJournal;
import com.vishop.notification.composition.dispatch.NotificationDispatchCoordinator;
import com.vishop.notification.composition.dispatch.NotificationDispatchPolicy;
import com.vishop.notification.composition.dispatch.RecipientDirectory;
import com.vishop.notification.modules.notification.application.api.NotificationDeliveryApi;
import com.vishop.notification.modules.notification.application.command.CreateNotificationCommandHandler;
import com.vishop.notification.modules.notification.application.command.EnqueueDeliveriesCommandHandler;
import com.vishop.notification.modules.notification.application.service.NotificationService;
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
  final NotificationService inbox = mock(NotificationService.class);
  final NotificationDeliveryApi delivery = mock(NotificationDeliveryApi.class);
  final RecipientDirectory directory = mock(RecipientDirectory.class);
  final TestJournal journal = new TestJournal();
  NotificationDispatchCoordinator coordinator;
  AnnotationConfigApplicationContext context;

  @BeforeEach
  void setupCqrs() {
    context = new AnnotationConfigApplicationContext();
    context.registerBean(NotificationService.class, () -> inbox);
    context.registerBean(NotificationDeliveryApi.class, () -> delivery);
    context.register(
        CqrsAutoConfiguration.class,
        CreateNotificationCommandHandler.class,
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
  void distinctEventsForTheSameSubjectDispatchWithoutCooldownWhileReplayIsSkipped() {
    UUID user = UUID.randomUUID();
    var channels = Set.of(NotificationChannel.IN_APP, NotificationChannel.EMAIL);
    var routes = Map.of(user, channels);
    var targets =
        List.of(
            new NotificationDeliveryApi.Target(
                user, NotificationChannel.EMAIL, "member@example.test"));
    var next =
        new NotificationMessage(
            message.tenantId(),
            "event-2",
            message.type(),
            message.subjectType(),
            message.subjectId(),
            message.requiredPermission(),
            message.shopId(),
            message.severity(),
            message.title(),
            message.body(),
            message.actionUrl(),
            message.occurredAt().plusSeconds(60));
    when(setting.resolve(eq(message.tenantId()), any()))
        .thenReturn(new NotificationDispatchPolicy.RoutingPolicy(channels, Set.of()));
    when(directory.eligible(any(), eq(Set.of()))).thenReturn(Set.of(user));
    when(setting.restrictForUser(message.tenantId(), user, message.type(), channels))
        .thenReturn(channels);
    when(directory.providerTargets(any(), eq(routes))).thenReturn(targets);
    when(inbox.create(any(), eq(Set.of(user)))).thenReturn(UUID.randomUUID(), UUID.randomUUID());
    when(delivery.enqueue(eq(message.tenantId()), any(), eq(targets)))
        .thenReturn(List.of(UUID.randomUUID()));

    var first = coordinator.dispatch(message).orElseThrow();
    var second = coordinator.dispatch(next).orElseThrow();
    assertThat(first.notificationId()).isPresent();
    assertThat(second.notificationId()).isPresent().isNotEqualTo(first.notificationId());
    assertThat(second.inAppRecipientCount()).isEqualTo(1);
    assertThat(second.deliveryIds()).hasSize(1);
    assertThat(coordinator.dispatch(message)).isEmpty();
    verify(inbox, times(2)).create(any(), eq(Set.of(user)));
    verify(delivery, times(2)).enqueue(eq(message.tenantId()), any(), eq(targets));
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
