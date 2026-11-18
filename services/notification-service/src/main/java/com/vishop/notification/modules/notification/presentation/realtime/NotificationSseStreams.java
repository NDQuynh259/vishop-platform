package com.vishop.notification.modules.notification.presentation.realtime;

import com.vishop.notification.modules.notification.application.dto.InboxActor;
import com.vishop.notification.modules.notification.application.dto.NotificationCreated;
import io.github.coreplatform.common.domain.exception.BadRequestException;
import io.github.coreplatform.common.domain.exception.ServiceUnavailableException;
import io.github.coreplatform.common.domain.exception.TooManyRequestsException;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** Instance-local, best-effort inbox signals. REST remains the source of truth after reconnect. */
public class NotificationSseStreams implements AutoCloseable {
  private static final Logger log = LoggerFactory.getLogger(NotificationSseStreams.class);
  // Keep authorization snapshots short-lived until session revocation is wired into this service.
  private static final long LIFETIME_MILLIS = 60_000;
  private final Clock clock;
  private final Supplier<SseEmitter> emitters;
  private final NotificationSseSettings settings;
  private final ConcurrentMap<UserKey, Set<Connection>> connectionsByUser =
      new ConcurrentHashMap<>();
  private final Object registryLock = new Object();
  // A closing connection retains its slot until its writer exits, bounding even stuck writers.
  private final Semaphore slots;
  private final ScheduledExecutorService heartbeat =
      Executors.newSingleThreadScheduledExecutor(
          Thread.ofPlatform().daemon().name("notification-sse-heartbeat").factory());
  private final ExecutorService writers;
  private final AtomicLong refreshVersion = new AtomicLong();
  private boolean closed;

  public NotificationSseStreams(Clock clock) {
    this(clock, new NotificationSseSettings());
  }

  public NotificationSseStreams(Clock clock, NotificationSseSettings settings) {
    this(clock, () -> new SseEmitter(LIFETIME_MILLIS), settings);
  }

  NotificationSseStreams(Clock clock, Supplier<SseEmitter> emitters) {
    this(clock, emitters, new NotificationSseSettings());
  }

  NotificationSseStreams(
      Clock clock, Supplier<SseEmitter> emitters, NotificationSseSettings settings) {
    this(
        clock,
        emitters,
        settings,
        new ThreadPoolExecutor(
            settings.writerThreads(),
            settings.writerThreads(),
            0,
            TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(settings.maxConnections()),
            Thread.ofPlatform().daemon().name("notification-sse-writer-", 0).factory()));
  }

  NotificationSseStreams(
      Clock clock,
      Supplier<SseEmitter> emitters,
      NotificationSseSettings settings,
      ExecutorService writers) {
    this.clock = clock;
    this.emitters = emitters;
    this.settings = settings;
    this.writers = writers;
    slots = new Semaphore(settings.maxConnections());
    long tickMillis = Math.min(1000, settings.heartbeatInterval().toMillis());
    heartbeat.scheduleWithFixedDelay(
        this::heartbeat, tickMillis, tickMillis, TimeUnit.MILLISECONDS);
  }

  public SseEmitter subscribe(InboxActor actor) {
    if (actor.getTenantId() == null || actor.getUserId() == null) {
      throw new BadRequestException("A tenant and user identity are required");
    }
    var key = new UserKey(actor.getTenantId(), actor.getUserId());
    final Connection connection;
    synchronized (registryLock) {
      if (closed) throw new ServiceUnavailableException("Notification stream unavailable", 3L);
      var existing = connectionsByUser.get(key);
      if ((existing != null && existing.size() >= settings.maxConnectionsPerUser())
          || !slots.tryAcquire()) {
        throw new TooManyRequestsException("Too many notification streams", 3L);
      }
      try {
        var emitter = emitters.get();
        connection =
            new Connection(
                key,
                actor,
                emitter,
                clock.instant().plusMillis(LIFETIME_MILLIS),
                refreshVersion.get());
        emitter.onCompletion(() -> disconnect(connection, false, null));
        emitter.onTimeout(() -> disconnect(connection, true, null));
        emitter.onError(error -> disconnect(connection, false, null));
        // Queue ready before publishing the connection; concurrent events cannot overtake it.
        connection.pending.add(ready());
        connection.writing = true;
        connectionsByUser
            .computeIfAbsent(key, ignored -> ConcurrentHashMap.newKeySet())
            .add(connection);
      } catch (RuntimeException ex) {
        slots.release();
        throw ex;
      }
    }
    // Never perform socket writes while holding the registry lock or on the after-commit thread.
    startWriter(connection);
    return connection.emitter;
  }

  public void broadcastLocal(NotificationCreated event) {
    var payload = Map.of("notificationId", event.notificationId(), "tenantId", event.tenantId());
    for (var recipient : event.recipients()) {
      var connections = connectionsByUser.get(new UserKey(event.tenantId(), recipient));
      if (connections == null) continue;
      for (var connection : connections) {
        if (connection.actor.getPermissions().contains(event.requiredPermission())) {
          enqueue(
              connection,
              SseEmitter.event()
                  .name("notification.created")
                  .data(payload, MediaType.APPLICATION_JSON));
        }
      }
    }
  }

  void heartbeat() {
    var now = clock.instant();
    long version = refreshVersion.get();
    for (var connections : connectionsByUser.values()) {
      for (var connection : connections) {
        if (!now.isBefore(connection.expiresAt)) {
          disconnect(connection, true, null);
          continue;
        }
        boolean refresh;
        synchronized (connection) {
          refresh = connection.active && connection.refreshVersion < version;
          if (refresh) connection.refreshVersion = version;
        }
        if (refresh) enqueue(connection, ready());
        synchronized (connection) {
          if (!connection.active
              || connection.writing
              || now.isBefore(connection.nextHeartbeatAt)) {
            continue;
          }
          connection.pending.add(SseEmitter.event().comment("heartbeat"));
          connection.writing = true;
        }
        startWriter(connection);
      }
    }
  }

  /**
   * Coalesces Redis recovery/overload signals; existing clients refetch on the next heartbeat tick.
   */
  public void requestRefreshLocal() {
    refreshVersion.incrementAndGet();
  }

  private static SseEmitter.SseEventBuilder ready() {
    return SseEmitter.event().name("ready").reconnectTime(3000).data(Map.of());
  }

  private void enqueue(Connection connection, SseEmitter.SseEventBuilder event) {
    boolean overflow;
    synchronized (connection) {
      if (!connection.active) return;
      overflow = connection.pending.size() >= settings.maxPendingEvents();
      if (!overflow) {
        connection.pending.add(event);
        if (connection.writing) return;
        connection.writing = true;
      }
    }
    if (overflow) disconnect(connection, true, null);
    else startWriter(connection);
  }

  private void startWriter(Connection connection) {
    try {
      writers.execute(new WriteTask(connection));
    } catch (RejectedExecutionException ex) {
      disconnect(connection, true, null);
      abandon(connection);
    }
  }

  private void abandon(Connection connection) {
    synchronized (connection) {
      connection.writing = false;
    }
    finish(connection);
  }

  private void drain(Connection connection) {
    while (true) {
      SseEmitter.SseEventBuilder event;
      synchronized (connection) {
        event = connection.active ? connection.pending.poll() : null;
        if (event == null) {
          connection.writing = false;
          break;
        }
      }
      if (!clock.instant().isBefore(connection.expiresAt)) {
        disconnect(connection, true, null);
        continue;
      }
      try {
        connection.emitter.send(event);
        synchronized (connection) {
          // Jitter spreads heartbeat bursts; business traffic postpones the next heartbeat.
          long interval = settings.heartbeatInterval().toMillis();
          long jitter = ThreadLocalRandom.current().nextLong(interval / 5 + 1);
          connection.nextHeartbeatAt = clock.instant().plusMillis(interval - jitter);
        }
      } catch (IOException | RuntimeException ex) {
        disconnect(connection, true, ex);
      }
    }
    finish(connection);
  }

  private void disconnect(Connection connection, boolean complete, Throwable error) {
    boolean idle;
    synchronized (connection) {
      if (!connection.active) return;
      connection.active = false;
      connection.complete = complete;
      connection.error = error;
      connection.pending.clear();
      idle = !connection.writing;
    }
    synchronized (registryLock) {
      var connections = connectionsByUser.get(connection.key);
      if (connections != null) {
        connections.remove(connection);
        if (connections.isEmpty()) connectionsByUser.remove(connection.key, connections);
      }
    }
    // An in-flight writer owns transport completion; producers never wait for its socket write.
    if (idle) finish(connection);
  }

  private void finish(Connection connection) {
    synchronized (connection) {
      if (connection.active || connection.writing || connection.finished) return;
      connection.finished = true;
    }
    try {
      if (connection.complete) {
        if (connection.error == null) connection.emitter.complete();
        else connection.emitter.completeWithError(connection.error);
      }
    } catch (RuntimeException ex) {
      log.debug("Failed to complete notification stream", ex);
    } finally {
      slots.release();
    }
  }

  @Override
  public void close() {
    final List<Connection> connections;
    synchronized (registryLock) {
      if (closed) return;
      closed = true;
      connections = connectionsByUser.values().stream().flatMap(Set::stream).toList();
    }
    heartbeat.shutdownNow();
    connections.forEach(connection -> disconnect(connection, true, null));
    // Queued tasks returned by shutdownNow never run, so release their connection slots here.
    for (var task : writers.shutdownNow()) {
      if (task instanceof WriteTask write) abandon(write.connection);
    }
  }

  private final class WriteTask implements Runnable {
    final Connection connection;

    WriteTask(Connection connection) {
      this.connection = connection;
    }

    @Override
    public void run() {
      drain(connection);
    }
  }

  private record UserKey(UUID tenantId, UUID userId) {}

  // The connection monitor protects mailbox/lifecycle metadata only, never socket I/O.
  private static final class Connection {
    final UserKey key;
    final InboxActor actor;
    final SseEmitter emitter;
    final Instant expiresAt;
    final ArrayDeque<SseEmitter.SseEventBuilder> pending = new ArrayDeque<>();
    Instant nextHeartbeatAt = Instant.MAX;
    boolean active = true;
    boolean writing;
    boolean finished;
    boolean complete;
    Throwable error;
    long refreshVersion;

    Connection(
        UserKey key, InboxActor actor, SseEmitter emitter, Instant expiresAt, long refreshVersion) {
      this.key = key;
      this.actor = actor;
      this.emitter = emitter;
      this.expiresAt = expiresAt;
      this.refreshVersion = refreshVersion;
    }
  }
}
