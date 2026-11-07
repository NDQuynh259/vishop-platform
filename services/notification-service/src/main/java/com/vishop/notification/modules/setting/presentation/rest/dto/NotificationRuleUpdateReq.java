package com.vishop.notification.modules.setting.presentation.rest.dto;

import com.vishop.notification.shared.contract.NotificationChannel;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class NotificationRuleUpdateReq {
  @NotNull private Boolean enabled;
  @NotNull private Set<@NotNull NotificationChannel> channels;
  @NotNull @PositiveOrZero private Long version;
}
