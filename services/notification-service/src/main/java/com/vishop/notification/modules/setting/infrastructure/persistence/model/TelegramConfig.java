package com.vishop.notification.modules.setting.infrastructure.persistence.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TelegramConfig(
    @JsonAlias({"endpointUrl", "connectionRef"}) String url,
    @JsonAlias({"credentialsRef", "botToken"}) String token,
    @JsonAlias("telegramChatId") String chatId) {
  public TelegramConfig(String token, String chatId) {
    this(null, token, chatId);
  }
}
