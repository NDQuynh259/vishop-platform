package com.vishop.notification.modules.announcement.application.command;

import com.vishop.notification.modules.announcement.application.api.NotificationAnnouncementApi;
import com.vishop.notification.modules.announcement.application.api.NotificationAnnouncementApi.Snapshot;
import io.github.coreplatform.common.domain.cqrs.CommandHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class CreateAnnouncementCommandHandler
    implements CommandHandler<CreateAnnouncementCommand, Snapshot> {
  private final NotificationAnnouncementApi application;

  @Override
  @Transactional
  public Snapshot handle(CreateAnnouncementCommand request) {
    return application.create(request.getDraft());
  }
}
