package com.vishop.notification.modules.setting.application.service;

import com.vishop.notification.modules.setting.domain.entity.NotificationSettingState.Connection;
import com.vishop.notification.shared.contract.NotificationChannel;
import io.github.coreplatform.common.domain.exception.BusinessRuleViolationException;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

final class NotificationConnectionConfiguration {
  private NotificationConnectionConfiguration() {}

  static void validate(NotificationChannel channel, Map<String, String> config) {
    Set<String> allowed =
        switch (channel) {
          case IN_APP -> Set.of();
          case TELEGRAM -> Set.of("url", "token", "chatId");
          case EMAIL -> Set.of("connectionRef", "credentialsRef", "fromAddress", "replyTo");
          case LARK -> Set.of("webhookUrl", "appSecretRef", "appToken");
        };
    if (!allowed.containsAll(config.keySet())) {
      throw invalid("connectionConfig contains fields unsupported by " + channel);
    }
    for (var field : config.entrySet()) {
      int max =
          switch (field.getKey()) {
            case "url", "connectionRef" -> 200;
            case "token", "credentialsRef", "appSecretRef", "appToken" -> 300;
            case "chatId" -> 100;
            case "fromAddress", "replyTo" -> 254;
            default -> 2048;
          };
      if (field.getValue() == null
          || field.getValue().isBlank()
          || field.getValue().length() > max) {
        throw invalid("Invalid connectionConfig." + field.getKey());
      }
    }
    for (String field : Set.of("url", "webhookUrl")) {
      String value = config.get(field);
      if (value == null) continue;
      try {
        var uri = new URI(value);
        if (!"https".equalsIgnoreCase(uri.getScheme())
            || uri.getHost() == null
            || uri.getUserInfo() != null
            || uri.getFragment() != null
            || (field.equals("url") && uri.getRawQuery() != null)) {
          throw invalid("connectionConfig." + field + " must be an HTTPS URL");
        }
      } catch (java.net.URISyntaxException ex) {
        throw invalid("Invalid connectionConfig." + field);
      }
    }
    for (String field : Set.of("fromAddress", "replyTo")) {
      String value = config.get(field);
      if (value != null && !value.matches("[^\\s@]+@[^\\s@]+")) {
        throw invalid("Invalid connectionConfig." + field);
      }
    }
  }

  static Map<String, Object> publicConfig(Connection connection) {
    Map<String, String> values = connection.connectionConfig();
    if (values == null) values = Map.of();
    var result = new LinkedHashMap<String, Object>();
    switch (connection.channel()) {
      case TELEGRAM -> {
        if (connection.chatId() != null) result.put("chatId", connection.chatId());
      }
      case EMAIL -> {
        for (String key : Set.of("fromAddress", "replyTo")) {
          if (values.containsKey(key)) result.put(key, values.get(key));
        }
      }
      case LARK, IN_APP -> {}
    }
    return Map.copyOf(result);
  }

  private static BusinessRuleViolationException invalid(String message) {
    return new BusinessRuleViolationException(message);
  }
}
