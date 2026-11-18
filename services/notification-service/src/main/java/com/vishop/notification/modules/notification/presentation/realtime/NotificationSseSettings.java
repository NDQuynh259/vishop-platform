package com.vishop.notification.modules.notification.presentation.realtime;

import java.time.Duration;

/** Runtime limits, independent of Spring configuration binding. */
public record NotificationSseSettings(
    int maxConnections,
    int maxConnectionsPerUser,
    int maxPendingEvents,
    int writerThreads,
    Duration heartbeatInterval) {
  public NotificationSseSettings() {
    this(1000, 5, 32, 4, Duration.ofSeconds(15));
  }

  public NotificationSseSettings {
    if (maxConnections < 1
        || maxConnectionsPerUser < 1
        || maxPendingEvents < 1
        || writerThreads < 1) {
      throw new IllegalArgumentException("SSE connection and queue limits must be positive");
    }
    if (heartbeatInterval == null
        || heartbeatInterval.toMillis() < 1
        || heartbeatInterval.compareTo(Duration.ofSeconds(60)) >= 0) {
      throw new IllegalArgumentException(
          "SSE heartbeat interval must be between 1ms and 60s (exclusive)");
    }
  }
}
