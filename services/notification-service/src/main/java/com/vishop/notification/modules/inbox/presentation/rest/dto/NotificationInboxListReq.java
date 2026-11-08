package com.vishop.notification.modules.inbox.presentation.rest.dto;

import com.vishop.notification.shared.constants.NotificationTypeCode;
import com.vishop.notification.shared.contract.NotificationMessage.Severity;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class NotificationInboxListReq {
  private NotificationTypeCode type;
  private Severity severity;
  private Boolean read;
  private UUID shopId;
}
