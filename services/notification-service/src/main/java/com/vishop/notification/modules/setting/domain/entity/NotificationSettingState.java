package com.vishop.notification.modules.setting.domain.entity;

import com.vishop.notification.shared.contract.NotificationChannel;
import java.util.Map;
import java.util.UUID;

public final class NotificationSettingState {
  private NotificationSettingState() {}

  /** Internal provider references must not be exposed through public responses. */
  public record Connection(
      NotificationChannel channel,
      boolean configured,
      boolean enabled,
      String connectionRef,
      String credentialsRef,
      String botUsername,
      String chatId,
      Long messageThreadId,
      long version,
      Map<String, String> connectionConfig,
      UUID id,
      String name) {
    public Connection {
      name = name == null ? channel.name() : name;
      connectionConfig = connectionConfig == null ? null : Map.copyOf(connectionConfig);
    }

    public Connection(
        NotificationChannel channel,
        boolean configured,
        boolean enabled,
        String connectionRef,
        String credentialsRef,
        String botUsername,
        String chatId,
        Long messageThreadId,
        long version,
        Map<String, String> connectionConfig,
        UUID id) {
      this(
          channel,
          configured,
          enabled,
          connectionRef,
          credentialsRef,
          botUsername,
          chatId,
          messageThreadId,
          version,
          connectionConfig,
          id,
          null);
    }

    public Connection withName(String name) {
      return new Connection(
          channel,
          configured,
          enabled,
          connectionRef,
          credentialsRef,
          botUsername,
          chatId,
          messageThreadId,
          version,
          connectionConfig,
          id,
          name);
    }

    public Connection(
        NotificationChannel channel,
        boolean configured,
        boolean enabled,
        String connectionRef,
        String credentialsRef,
        String botUsername,
        String chatId,
        Long messageThreadId,
        long version,
        Map<String, String> connectionConfig) {
      this(
          channel,
          configured,
          enabled,
          connectionRef,
          credentialsRef,
          botUsername,
          chatId,
          messageThreadId,
          version,
          connectionConfig,
          null);
    }

    public Connection(
        NotificationChannel channel,
        boolean configured,
        boolean enabled,
        String connectionRef,
        String credentialsRef,
        String botUsername,
        String chatId,
        Long messageThreadId,
        long version) {
      this(
          channel,
          configured,
          enabled,
          connectionRef,
          credentialsRef,
          botUsername,
          chatId,
          messageThreadId,
          version,
          null);
    }

    @Override
    public String toString() {
      return "Connection[channel="
          + channel
          + ", enabled="
          + enabled
          + ", version="
          + version
          + ", config=redacted]";
    }
  }
}
