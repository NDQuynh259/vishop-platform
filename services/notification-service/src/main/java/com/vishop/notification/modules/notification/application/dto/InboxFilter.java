package com.vishop.notification.modules.notification.application.dto;

import com.vishop.notification.shared.constants.NotificationTypeCode;
import com.vishop.notification.shared.contract.NotificationMessage.Severity;
import io.github.coreplatform.common.domain.pagination.CursorParams;
import io.github.coreplatform.common.domain.pagination.OffsetPagination;
import java.util.UUID;
import lombok.Value;

@Value
public class InboxFilter {
  NotificationTypeCode type;
  Severity severity;
  Boolean read;
  UUID shopId;
  OffsetPagination pagination;
  CursorParams cursorParams;

  public int getLimit() {
    return pagination != null ? pagination.limit() : cursorParams.limit();
  }

  public String getCursor() {
    return cursorParams == null ? null : cursorParams.cursor();
  }
}
