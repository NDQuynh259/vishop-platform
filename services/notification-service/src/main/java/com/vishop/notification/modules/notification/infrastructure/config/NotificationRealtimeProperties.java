package com.vishop.notification.modules.notification.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("notification.realtime")
public record NotificationRealtimeProperties(
    @DefaultValue("redis") Transport transport,
    @DefaultValue("vishop:notification:created:v1") String channel,
    @DefaultValue("256") int publisherQueueCapacity,
    @DefaultValue("256") int listenerQueueCapacity,
    @DefaultValue("1048576") int maxMessageBytes) {
  public enum Transport {
    REDIS,
    LOCAL
  }

  public NotificationRealtimeProperties() {
    this(Transport.REDIS, "vishop:notification:created:v1", 256, 256, 1048576);
  }

  @ConstructorBinding
  public NotificationRealtimeProperties {
    if (transport == null || channel == null || channel.isBlank()) {
      throw new IllegalArgumentException("Notification transport and Redis channel are required");
    }
    if (publisherQueueCapacity < 1 || listenerQueueCapacity < 1 || maxMessageBytes < 1) {
      throw new IllegalArgumentException(
          "Notification Redis queue and message limits must be positive");
    }
  }
}
