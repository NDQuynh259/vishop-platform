package com.vishop.notification.modules.inbox.application.query;

import com.vishop.notification.modules.inbox.application.dto.NotificationView;
import com.vishop.notification.modules.inbox.application.service.NotificationInboxApplicationService;
import io.github.coreplatform.common.domain.cqrs.QueryHandler;
import io.github.coreplatform.common.domain.pagination.PagedResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class ListInboxQueryHandler
    implements QueryHandler<ListInboxQuery, PagedResult<NotificationView>> {
  private final NotificationInboxApplicationService service;

  @Override
  @Transactional(readOnly = true)
  public PagedResult<NotificationView> handle(ListInboxQuery request) {
    return service.list(request.getActor(), request.getFilter());
  }
}
