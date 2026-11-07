package com.vishop.notification.modules.inbox.application.query;

import com.vishop.notification.modules.inbox.application.dto.InboxActor;
import com.vishop.notification.modules.inbox.application.dto.InboxFilter;
import com.vishop.notification.modules.inbox.application.dto.NotificationView;
import io.github.coreplatform.common.domain.cqrs.Query;
import io.github.coreplatform.common.domain.pagination.PagedResult;
import lombok.Value;

@Value
public class ListInboxQuery implements Query<PagedResult<NotificationView>> {
  InboxActor actor;
  InboxFilter filter;
}
