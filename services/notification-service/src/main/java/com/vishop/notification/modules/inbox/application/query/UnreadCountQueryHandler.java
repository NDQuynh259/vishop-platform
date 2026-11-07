package com.vishop.notification.modules.inbox.application.query;

import com.vishop.notification.modules.inbox.application.dto.UnreadCount;
import com.vishop.notification.modules.inbox.application.service.NotificationInboxApplicationService;
import io.github.coreplatform.common.domain.cqrs.QueryHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class UnreadCountQueryHandler implements QueryHandler<UnreadCountQuery, UnreadCount> {
  private final NotificationInboxApplicationService service;

  @Override
  @Transactional(readOnly = true)
  public UnreadCount handle(UnreadCountQuery request) {
    return service.unreadCount(request.getActor());
  }
}
