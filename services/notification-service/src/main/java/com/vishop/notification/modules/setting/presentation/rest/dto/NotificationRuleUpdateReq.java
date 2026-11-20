package com.vishop.notification.modules.setting.presentation.rest.dto;

import com.vishop.notification.shared.constants.NotificationFeatureCode;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class NotificationRuleUpdateReq {
  @NotNull private Boolean enabled;
  @NotNull private NotificationFeatureCode featureCode;
  @NotNull @PositiveOrZero private Long version;
}
