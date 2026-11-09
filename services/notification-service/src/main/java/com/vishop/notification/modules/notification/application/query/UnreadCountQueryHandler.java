package com.vishop.notification.modules.notification.application.query;

import com.vishop.notification.modules.notification.application.dto.UnreadCount;
import com.vishop.notification.modules.notification.application.service.NotificationService;
import io.github.coreplatform.common.domain.cqrs.QueryHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class UnreadCountQueryHandler implements QueryHandler<UnreadCountQuery, UnreadCount> {
  private final NotificationService service;

  @Override
  @Transactional(readOnly = true)
  public UnreadCount handle(UnreadCountQuery request) {
    return service.unreadCount(request.getActor());
  }
}
