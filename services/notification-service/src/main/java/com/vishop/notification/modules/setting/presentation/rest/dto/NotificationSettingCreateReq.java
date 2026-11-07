package com.vishop.notification.modules.setting.presentation.rest.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.vishop.notification.shared.contract.NotificationChannel;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class NotificationSettingCreateReq {
  @Size(min = 1, max = 150)
  @Pattern(regexp = "(?s).*\\S.*")
  private String name;

  @NotNull private NotificationChannel channel;

  @NotNull private Boolean enabled;

  @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
  private JsonNode connectionConfig;

  @Override
  public String toString() {
    return "NotificationSettingCreateReq[redacted]";
  }
}
