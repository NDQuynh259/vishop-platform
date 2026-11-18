package com.vishop.notification.modules.notification.presentation.realtime;

import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.vishop.notification.modules.notification.application.dto.InboxActor;
import com.vishop.notification.modules.notification.application.dto.NotificationCreated;
import io.github.coreplatform.common.domain.exception.ServiceUnavailableException;
import io.github.coreplatform.common.domain.exception.TooManyRequestsException;
import java.io.IOException;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

class NotificationSseStreamsTest {
  final UUID tenant = UUID.randomUUID(), user = UUID.randomUUID(), id = UUID.randomUUID();
  final Clock clock = Clock.fixed(Instant.parse("2026-11-12T00:00:00Z"), ZoneOffset.UTC);

  InboxActor actor(UUID tenantId, UUID userId, String... permissions) {
    return new InboxActor(tenantId, userId, Set.of(permissions));
  }

  @Test
  void onlyAuthorizedRecipientsInTheSameTenantReceiveSignalsOnEveryTab() throws Exception {
    var emitters = new ArrayList<SseEmitter>();
    try (var streams =
        new NotificationSseStreams(
            clock,
            () -> {
              var emitter = mock(SseEmitter.class);
              emitters.add(emitter);
              return emitter;
            })) {
      streams.subscribe(actor(tenant, user, "inventory.view"));
      streams.subscribe(actor(tenant, user, "inventory.view"));
      streams.subscribe(actor(UUID.randomUUID(), user, "inventory.view"));
      streams.subscribe(actor(tenant, UUID.randomUUID(), "inventory.view"));
      streams.subscribe(actor(tenant, user, "finance.view"));
      streams.broadcastLocal(new NotificationCreated(id, tenant, Set.of(user), "inventory.view"));
      verify(emitters.get(0), timeout(2000).times(2)).send(any(SseEmitter.SseEventBuilder.class));
      verify(emitters.get(1), timeout(2000).times(2)).send(any(SseEmitter.SseEventBuilder.class));
      for (int i = 2; i < emitters.size(); i++) {
        verify(emitters.get(i), timeout(2000)).send(any(SseEmitter.SseEventBuilder.class));
      }
    }
  }

  @Test
  void failedClientDoesNotBreakOtherStreamsAndIsRemoved() throws Exception {
    var broken = mock(SseEmitter.class);
    var healthy = mock(SseEmitter.class);
    var queue = new ArrayDeque<>(List.of(broken, healthy));
    try (var streams = new NotificationSseStreams(clock, queue::remove)) {
      streams.subscribe(actor(tenant, user, "inventory.view"));
      streams.subscribe(actor(tenant, user, "inventory.view"));
      verify(broken, timeout(2000)).send(any(SseEmitter.SseEventBuilder.class));
      verify(healthy, timeout(2000)).send(any(SseEmitter.SseEventBuilder.class));
      doThrow(new IOException("Disconnected"))
          .when(broken)
          .send(any(SseEmitter.SseEventBuilder.class));
      streams.broadcastLocal(new NotificationCreated(id, tenant, Set.of(user), "inventory.view"));
      await()
          .atMost(Duration.ofSeconds(2))
          .untilAsserted(() -> verify(broken).completeWithError(any(IOException.class)));
      verify(healthy, timeout(2000).times(2)).send(any(SseEmitter.SseEventBuilder.class));
      streams.heartbeat();
      verify(healthy, times(2)).send(any(SseEmitter.SseEventBuilder.class));
      verify(broken, times(2)).send(any(SseEmitter.SseEventBuilder.class));
    }
  }

  @Test
  void completionAndTimeoutReleaseConnectionSlots() {
    var emitters = new ArrayList<SseEmitter>();
    try (var streams =
        new NotificationSseStreams(
            clock,
            () -> {
              var emitter = mock(SseEmitter.class);
              emitters.add(emitter);
              return emitter;
            })) {
      var actor = actor(tenant, user, "inventory.view");
      for (int i = 0; i < 5; i++) streams.subscribe(actor);
      var completions = ArgumentCaptor.forClass(Runnable.class);
      verify(emitters.getFirst()).onCompletion(completions.capture());
      completions.getValue().run();
      streams.subscribe(actor);
      var timeouts = ArgumentCaptor.forClass(Runnable.class);
      verify(emitters.getLast()).onTimeout(timeouts.capture());
      timeouts.getValue().run();
      await()
          .atMost(Duration.ofSeconds(2))
          .untilAsserted(() -> verify(emitters.getLast()).complete());
    }
  }

  @Test
  void connectionLimitIsPerTenantUserAndShutdownRejectsNewConnections() {
    var streams = new NotificationSseStreams(clock, () -> mock(SseEmitter.class));
    try {
      var actor = actor(tenant, user, "inventory.view");
      for (int i = 0; i < 5; i++) streams.subscribe(actor);
      assertThatThrownBy(() -> streams.subscribe(actor))
          .isInstanceOfSatisfying(
              TooManyRequestsException.class,
              e -> assertThat(e.getRetryAfterSeconds()).isEqualTo(3));
      streams.subscribe(actor(UUID.randomUUID(), user, "inventory.view"));
    } finally {
      streams.close();
    }
    assertThatThrownBy(() -> streams.subscribe(actor(tenant, user)))
        .isInstanceOfSatisfying(
            ServiceUnavailableException.class,
            e -> assertThat(e.getRetryAfterSeconds()).isEqualTo(3));
  }

  @Test
  void heartbeatExpiresConnectionsAndPreventsSendingWithOldAuthorization() throws Exception {
    var clock = mock(Clock.class);
    Instant start = Instant.parse("2026-11-12T00:00:00Z");
    when(clock.instant()).thenReturn(start);
    var emitter = mock(SseEmitter.class);
    try (var manual = new ManualStreams(clock, new NotificationSseSettings(), () -> emitter)) {
      var streams = manual.streams;
      streams.subscribe(actor(tenant, user, "inventory.view"));
      manual.flush();
      when(clock.instant()).thenReturn(start.plusSeconds(60));
      streams.heartbeat();
      verify(emitter).complete();
      streams.broadcastLocal(new NotificationCreated(id, tenant, Set.of(user), "inventory.view"));
      verify(emitter).send(any(SseEmitter.SseEventBuilder.class));
    }
  }

  @Test
  void readyAndEveryNotificationAreSentInOrderByOneWriter() throws Exception {
    var emitter = mock(SseEmitter.class);
    try (var manual = new ManualStreams(clock, new NotificationSseSettings(), () -> emitter)) {
      manual.streams.subscribe(actor(tenant, user, "inventory.view"));
      var secondId = UUID.randomUUID();
      manual.streams.broadcastLocal(
          new NotificationCreated(id, tenant, Set.of(user), "inventory.view"));
      manual.streams.broadcastLocal(
          new NotificationCreated(secondId, tenant, Set.of(user), "inventory.view"));
      assertThat(manual.tasks).hasSize(1);
      manual.flush();
      var events = ArgumentCaptor.forClass(SseEmitter.SseEventBuilder.class);
      verify(emitter, times(3)).send(events.capture());
      assertThat(
              events.getAllValues().stream()
                  .flatMap(event -> event.build().stream())
                  .map(part -> part.getData())
                  .filter(Map.class::isInstance)
                  .toList())
          .containsExactly(
              Map.of(),
              Map.of("notificationId", id, "tenantId", tenant),
              Map.of("notificationId", secondId, "tenantId", tenant));
    }
  }

  @Test
  void heartbeatOnlyWritesToIdleConnectionsAndNeverQueuesBehindAnExistingWriter() throws Exception {
    var clock = mock(Clock.class);
    var start = Instant.parse("2026-11-12T00:00:00Z");
    when(clock.instant()).thenReturn(start);
    var emitter = mock(SseEmitter.class);
    try (var manual = new ManualStreams(clock, new NotificationSseSettings(), () -> emitter)) {
      manual.streams.subscribe(actor(tenant, user, "inventory.view"));
      manual.flush();
      manual.streams.heartbeat();
      assertThat(manual.tasks).isEmpty();
      when(clock.instant()).thenReturn(start.plusSeconds(15));
      manual.streams.heartbeat();
      manual.streams.heartbeat();
      assertThat(manual.tasks).hasSize(1);
      manual.flush();
      when(clock.instant()).thenReturn(start.plusSeconds(25));
      manual.streams.broadcastLocal(
          new NotificationCreated(id, tenant, Set.of(user), "inventory.view"));
      manual.flush();
      when(clock.instant()).thenReturn(start.plusSeconds(30));
      manual.streams.heartbeat();
      assertThat(manual.tasks).isEmpty();
      verify(emitter, times(3)).send(any(SseEmitter.SseEventBuilder.class));
    }
  }

  @Test
  void overflowingOneMailboxDisconnectsOnlyThatClientAndRetainsCapacityUntilItsWriterExits()
      throws Exception {
    var emitters = new ArrayDeque<>(List.of(mock(SseEmitter.class), mock(SseEmitter.class)));
    var slow = emitters.getFirst();
    var healthy = emitters.getLast();
    var properties = new NotificationSseSettings(2, 2, 2, 2, Duration.ofSeconds(15));
    try (var manual = new ManualStreams(clock, properties, emitters::remove)) {
      manual.streams.subscribe(actor(tenant, user, "inventory.view"));
      var healthyUser = UUID.randomUUID();
      manual.streams.subscribe(actor(tenant, healthyUser, "inventory.view"));
      // Slow client's ready and first notification fill its mailbox before its writer runs.
      manual.streams.broadcastLocal(
          new NotificationCreated(id, tenant, Set.of(user), "inventory.view"));
      manual.streams.broadcastLocal(
          new NotificationCreated(id, tenant, Set.of(user), "inventory.view"));
      assertThatThrownBy(() -> manual.streams.subscribe(actor(tenant, UUID.randomUUID())))
          .isInstanceOfSatisfying(
              TooManyRequestsException.class,
              error -> assertThat(error.getRetryAfterSeconds()).isEqualTo(3));
      manual.streams.broadcastLocal(
          new NotificationCreated(id, tenant, Set.of(healthyUser), "inventory.view"));
      manual.flush();
      verify(slow, never()).send(any(SseEmitter.SseEventBuilder.class));
      verify(slow).complete();
      verify(healthy, times(2)).send(any(SseEmitter.SseEventBuilder.class));
    }
  }

  @Test
  void queuedEventsAreDiscardedIfAuthorizationExpiresBeforeWriting() throws Exception {
    var clock = mock(Clock.class);
    var start = Instant.parse("2026-11-12T00:00:00Z");
    when(clock.instant()).thenReturn(start);
    var emitter = mock(SseEmitter.class);
    try (var manual = new ManualStreams(clock, new NotificationSseSettings(), () -> emitter)) {
      manual.streams.subscribe(actor(tenant, user, "inventory.view"));
      manual.flush();
      manual.streams.broadcastLocal(
          new NotificationCreated(id, tenant, Set.of(user), "inventory.view"));
      when(clock.instant()).thenReturn(start.plusSeconds(60));
      manual.flush();
      verify(emitter).send(any(SseEmitter.SseEventBuilder.class));
      verify(emitter).complete();
    }
  }

  @Test
  void oneBlockedClientCannotOccupyMultipleWorkersOrBlockProducers() throws Exception {
    var slow = mock(SseEmitter.class);
    var healthy = mock(SseEmitter.class);
    var emitters = new ArrayDeque<>(List.of(slow, healthy));
    var entered = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    var properties = new NotificationSseSettings(10, 5, 2, 2, Duration.ofSeconds(15));
    try (var streams = new NotificationSseStreams(clock, emitters::remove, properties)) {
      streams.subscribe(actor(tenant, user, "inventory.view"));
      verify(slow, timeout(2000)).send(any(SseEmitter.SseEventBuilder.class));
      doAnswer(
              invocation -> {
                entered.countDown();
                if (!release.await(5, TimeUnit.SECONDS))
                  throw new IOException("Test writer timed out");
                return null;
              })
          .when(slow)
          .send(any(SseEmitter.SseEventBuilder.class));
      streams.broadcastLocal(new NotificationCreated(id, tenant, Set.of(user), "inventory.view"));
      assertThat(entered.await(2, TimeUnit.SECONDS)).isTrue();
      org.junit.jupiter.api.Assertions.assertTimeoutPreemptively(
          Duration.ofSeconds(1),
          () -> {
            for (int i = 0; i < 5; i++) {
              streams.broadcastLocal(
                  new NotificationCreated(id, tenant, Set.of(user), "inventory.view"));
            }
          });
      streams.subscribe(actor(tenant, UUID.randomUUID(), "inventory.view"));
      verify(healthy, timeout(2000)).send(any(SseEmitter.SseEventBuilder.class));
      verify(slow, never()).complete();
      release.countDown();
      await().atMost(Duration.ofSeconds(2)).untilAsserted(() -> verify(slow).complete());
      verify(slow, times(2)).send(any(SseEmitter.SseEventBuilder.class));
    } finally {
      release.countDown();
    }
  }

  @Test
  void duplicateLifecycleCallbacksCannotReleaseAnotherConnectionsSlot() {
    var emitter = mock(SseEmitter.class);
    var properties = new NotificationSseSettings(1, 1, 2, 1, Duration.ofSeconds(15));
    try (var manual = new ManualStreams(clock, properties, () -> emitter)) {
      manual.streams.subscribe(actor(tenant, user));
      manual.flush();
      var callback = ArgumentCaptor.forClass(Runnable.class);
      verify(emitter).onCompletion(callback.capture());
      callback.getValue().run();
      callback.getValue().run();
      manual.streams.subscribe(actor(tenant, user));
      assertThatThrownBy(() -> manual.streams.subscribe(actor(tenant, UUID.randomUUID())))
          .isInstanceOf(TooManyRequestsException.class);
    }
  }

  @Test
  void shutdownCompletesQueuedConnectionsWithoutSendingTheirBufferedEvents() throws Exception {
    var emitter = mock(SseEmitter.class);
    try (var manual = new ManualStreams(clock, new NotificationSseSettings(), () -> emitter)) {
      manual.streams.subscribe(actor(tenant, user));
      manual.streams.close();
      verify(emitter).complete();
      verify(emitter, never()).send(any(SseEmitter.SseEventBuilder.class));
      assertThat(manual.tasks).isEmpty();
    }
  }

  @Test
  void concurrentSubscriptionsCannotExceedTheTenantUserLimit() throws Exception {
    var properties = new NotificationSseSettings(20, 5, 32, 2, Duration.ofSeconds(15));
    try (var streams = new NotificationSseStreams(clock, () -> mock(SseEmitter.class), properties);
        var requests = Executors.newFixedThreadPool(10)) {
      var attempts = new ArrayList<Callable<Boolean>>();
      for (int i = 0; i < 50; i++) {
        attempts.add(
            () -> {
              try {
                streams.subscribe(actor(tenant, user));
                return true;
              } catch (TooManyRequestsException error) {
                assertThat(error.getRetryAfterSeconds()).isEqualTo(3);
                return false;
              }
            });
      }
      int accepted = 0;
      for (var result : requests.invokeAll(attempts)) {
        if (result.get()) accepted++;
      }
      assertThat(accepted).isEqualTo(5);
      streams.subscribe(actor(UUID.randomUUID(), user));
    }
  }

  @Test
  void recoveryRefreshesExistingStreamsOnceAndDoesNotRefreshNewConnectionsTwice() throws Exception {
    var old = mock(SseEmitter.class);
    var fresh = mock(SseEmitter.class);
    var emitters = new ArrayDeque<>(List.of(old, fresh));
    try (var manual = new ManualStreams(clock, new NotificationSseSettings(), emitters::remove)) {
      manual.streams.subscribe(actor(tenant, user));
      manual.flush();
      for (int i = 0; i < 10; i++) manual.streams.requestRefreshLocal();
      manual.streams.subscribe(actor(tenant, user));
      manual.flush();
      manual.streams.heartbeat();
      manual.flush();
      manual.streams.heartbeat();
      manual.flush();
      verify(old, times(2)).send(any(SseEmitter.SseEventBuilder.class));
      verify(fresh).send(any(SseEmitter.SseEventBuilder.class));
      var events = ArgumentCaptor.forClass(SseEmitter.SseEventBuilder.class);
      verify(old, times(2)).send(events.capture());
      assertThat(events.getValue().build().stream().map(part -> part.getData()).toList())
          .anyMatch(part -> part instanceof String text && text.contains("event:ready"));
    }
  }

  private static final class ManualStreams implements AutoCloseable {
    final Deque<Runnable> tasks = new ArrayDeque<>();
    final NotificationSseStreams streams;

    ManualStreams(
        Clock clock,
        NotificationSseSettings properties,
        java.util.function.Supplier<SseEmitter> emitters) {
      var writers = mock(ExecutorService.class);
      doAnswer(
              invocation -> {
                tasks.add(invocation.getArgument(0));
                return null;
              })
          .when(writers)
          .execute(any(Runnable.class));
      when(writers.shutdownNow())
          .thenAnswer(
              invocation -> {
                var remaining = new ArrayList<>(tasks);
                tasks.clear();
                return remaining;
              });
      streams = new NotificationSseStreams(clock, emitters, properties, writers);
    }

    void flush() {
      while (!tasks.isEmpty()) tasks.removeFirst().run();
    }

    @Override
    public void close() {
      streams.close();
    }
  }
}
