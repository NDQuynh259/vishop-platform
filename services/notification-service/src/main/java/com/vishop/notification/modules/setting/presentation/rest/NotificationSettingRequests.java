package com.vishop.notification.modules.setting.presentation.rest;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.*;
import java.util.Map;

public final class NotificationSettingRequests {
  private NotificationSettingRequests() {}

  public record Channel(
      @NotNull Boolean enabled,
      @JsonProperty(access = JsonProperty.Access.WRITE_ONLY) @Size(min = 1, max = 4096)
          String botToken,
      @Size(min = 1, max = 100) String chatId,
      @Positive Long messageThreadId,
      @NotNull @PositiveOrZero Long version,
      @JsonProperty(access = JsonProperty.Access.WRITE_ONLY) @Size(max = 5)
          Map<@NotBlank String, @NotBlank @Size(max = 2048) String> connectionConfig) {
    @Override
    public String toString() {
      return "Channel[redacted]";
    }
  }
}
