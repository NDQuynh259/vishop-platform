package com.vishop.notification.modules.inbox.application.query;

import com.vishop.notification.modules.inbox.application.dto.NotificationView;
import com.vishop.notification.modules.inbox.application.service.NotificationInboxApplicationService;
import io.github.coreplatform.common.domain.cqrs.QueryHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class GetNotificationQueryHandler
    implements QueryHandler<GetNotificationQuery, NotificationView> {
  private final NotificationInboxApplicationService service;

  @Override
  @Transactional(readOnly = true)
  public NotificationView handle(GetNotificationQuery request) {
    return service.getById(request.getActor(), request.getNotificationId());
  }
}
