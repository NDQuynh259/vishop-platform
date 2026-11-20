package com.vishop.notification.modules.setting.domain.entity;

import com.vishop.notification.shared.constants.NotificationFeatureCode;
import java.util.UUID;
import lombok.Value;

@Value
public class NotificationAlertRule {
  UUID id;
  String type;
  boolean enabled;
  NotificationFeatureCode featureCode;
  long version;
}
