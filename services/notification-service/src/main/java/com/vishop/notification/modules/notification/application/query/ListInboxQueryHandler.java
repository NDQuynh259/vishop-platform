package com.vishop.notification.modules.notification.application.query;

import com.vishop.notification.modules.notification.application.dto.NotificationView;
import com.vishop.notification.modules.notification.application.service.NotificationService;
import io.github.coreplatform.common.domain.cqrs.QueryHandler;
import io.github.coreplatform.common.domain.pagination.PagedResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class ListInboxQueryHandler
    implements QueryHandler<ListInboxQuery, PagedResult<NotificationView>> {
  private final NotificationService service;

  @Override
  @Transactional(readOnly = true)
  public PagedResult<NotificationView> handle(ListInboxQuery request) {
    return service.list(request.getActor(), request.getFilter());
  }
}
