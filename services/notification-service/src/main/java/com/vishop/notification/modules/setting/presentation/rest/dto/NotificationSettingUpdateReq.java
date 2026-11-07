package com.vishop.notification.modules.setting.presentation.rest.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.*;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public final class NotificationSettingUpdateReq {

  @Size(min = 1, max = 150)
  @Pattern(regexp = "(?s).*\\S.*")
  private String name;

  @NotNull private Boolean enabled;

  @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
  @Size(min = 1, max = 4096)
  private String botToken;

  @Size(min = 1, max = 100)
  private String chatId;

  @Positive private Long messageThreadId;

  @NotNull @PositiveOrZero private Long version;

  // The stored record's channel determines the concrete connection DTO.
  @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
  private JsonNode connectionConfig;

  @Override
  public String toString() {
    return "NotificationSettingUpdateReq[redacted]";
  }

  public abstract static class ConnectionConfig {
    public abstract Map<String, String> toMap();

    @JsonAnySetter
    public final void unsupportedField(String name, Object value) {
      throw new IllegalArgumentException("Unsupported connectionConfig field");
    }

    @Override
    public final String toString() {
      return "ConnectionConfig[redacted]";
    }

    protected static Map<String, String> values(String... fields) {
      var result = new LinkedHashMap<String, String>();
      for (int i = 0; i < fields.length; i += 2) {
        if (fields[i + 1] != null) result.put(fields[i], fields[i + 1]);
      }
      return result;
    }
  }

  public static final class InAppConfig extends ConnectionConfig {
    @Override
    public Map<String, String> toMap() {
      return Map.of();
    }
  }

  @Getter
  @Setter
  public static final class TelegramConfig extends ConnectionConfig {
    @Size(min = 1, max = 200)
    @Pattern(regexp = "(?s).*\\S.*")
    private String url;

    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @Size(min = 1, max = 300)
    @Pattern(regexp = "(?s).*\\S.*")
    private String token;

    @Size(min = 1, max = 100)
    @Pattern(regexp = "(?s).*\\S.*")
    private String chatId;

    @Override
    public Map<String, String> toMap() {
      return values("url", url, "token", token, "chatId", chatId);
    }
  }

  @Getter
  @Setter
  public static final class EmailConfig extends ConnectionConfig {
    @Size(min = 1, max = 200)
    @Pattern(regexp = "(?s).*\\S.*")
    private String connectionRef;

    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @Size(min = 1, max = 300)
    @Pattern(regexp = "(?s).*\\S.*")
    private String credentialsRef;

    @Email
    @Size(min = 1, max = 254)
    @Pattern(regexp = "(?s).*\\S.*")
    private String fromAddress;

    @Email
    @Size(min = 1, max = 254)
    @Pattern(regexp = "(?s).*\\S.*")
    private String replyTo;

    @Override
    public Map<String, String> toMap() {
      return values(
          "connectionRef",
          connectionRef,
          "credentialsRef",
          credentialsRef,
          "fromAddress",
          fromAddress,
          "replyTo",
          replyTo);
    }
  }

  @Getter
  @Setter
  public static final class LarkConfig extends ConnectionConfig {
    @Size(min = 1, max = 2048)
    @Pattern(regexp = "(?s).*\\S.*")
    private String webhookUrl;

    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @Size(min = 1, max = 300)
    @Pattern(regexp = "(?s).*\\S.*")
    private String appSecretRef;

    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @Size(min = 1, max = 300)
    @Pattern(regexp = "(?s).*\\S.*")
    private String appToken;

    @Override
    public Map<String, String> toMap() {
      return values("webhookUrl", webhookUrl, "appSecretRef", appSecretRef, "appToken", appToken);
    }
  }
}
