package com.vishop.notification.modules.inbox.application.dto;

import com.vishop.notification.shared.constants.NotificationTypeCode;
import com.vishop.notification.shared.contract.NotificationMessage.Severity;
import java.util.UUID;
import lombok.Value;

@Value
public class InboxFilter {
  NotificationTypeCode type;
  Severity severity;
  Boolean read;
  UUID shopId;
  int limit;
  String cursor;
}
