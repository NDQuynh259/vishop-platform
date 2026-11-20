package com.vishop.notification.modules.setting.presentation.rest.dto;

import com.vishop.notification.shared.constants.NotificationFeatureCode;
import com.vishop.notification.shared.constants.NotificationTypeCode;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class NotificationRuleCreateReq {
  @NotNull private NotificationTypeCode type;
  @NotNull private Boolean enabled;
  @NotNull private NotificationFeatureCode featureCode;
}
