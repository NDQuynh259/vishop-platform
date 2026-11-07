package com.vishop.notification.modules.setting.presentation.rest.dto;

import com.vishop.notification.shared.constants.NotificationTypeCode;
import com.vishop.notification.shared.contract.NotificationChannel;
import jakarta.validation.constraints.NotNull;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class NotificationRuleCreateReq {
  @NotNull private NotificationTypeCode type;
  @NotNull private Boolean enabled;
  @NotNull private Set<@NotNull NotificationChannel> channels;
}
