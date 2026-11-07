package com.vishop.notification.modules.announcement.application.command;

import com.vishop.notification.modules.announcement.application.api.NotificationAnnouncementApi;
import com.vishop.notification.modules.announcement.application.api.NotificationAnnouncementApi.Snapshot;
import io.github.coreplatform.common.domain.cqrs.CommandHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class PublishAnnouncementCommandHandler
    implements CommandHandler<PublishAnnouncementCommand, Snapshot> {
  private final NotificationAnnouncementApi application;

  @Override
  @Transactional
  public Snapshot handle(PublishAnnouncementCommand request) {
    return application.publish(request.getId(), request.getExpectedVersion());
  }
}
