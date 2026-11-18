package com.vishop.notification.modules.notification.infrastructure.config;

import com.vishop.notification.modules.notification.presentation.realtime.NotificationSseSettings;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("notification.sse")
public record NotificationSseProperties(
    @DefaultValue("1000") int maxConnections,
    @DefaultValue("5") int maxConnectionsPerUser,
    @DefaultValue("32") int maxPendingEvents,
    @DefaultValue("4") int writerThreads,
    @DefaultValue("15s") Duration heartbeatInterval) {
  public NotificationSseProperties() {
    this(1000, 5, 32, 4, Duration.ofSeconds(15));
  }

  @ConstructorBinding
  public NotificationSseProperties {
    new NotificationSseSettings(
        maxConnections, maxConnectionsPerUser, maxPendingEvents, writerThreads, heartbeatInterval);
  }

  public NotificationSseSettings toSettings() {
    return new NotificationSseSettings(
        maxConnections, maxConnectionsPerUser, maxPendingEvents, writerThreads, heartbeatInterval);
  }
}
