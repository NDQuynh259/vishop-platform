package com.vishop.notification.modules.announcement.application.command;

import com.vishop.notification.modules.announcement.application.api.NotificationAnnouncementApi.Draft;
import com.vishop.notification.modules.announcement.application.api.NotificationAnnouncementApi.Snapshot;
import io.github.coreplatform.common.domain.cqrs.Command;
import lombok.ToString;
import lombok.Value;

@Value
@ToString(onlyExplicitlyIncluded = true)
public class CreateAnnouncementCommand implements Command<Snapshot> {
  Draft draft;
}
