package com.vishop.notification.modules.announcement.application.command;

import com.vishop.notification.modules.announcement.application.api.NotificationAnnouncementApi.Snapshot;
import io.github.coreplatform.common.domain.cqrs.Command;
import java.util.UUID;
import lombok.ToString;
import lombok.Value;

@Value
@ToString(onlyExplicitlyIncluded = true)
public class PublishAnnouncementCommand implements Command<Snapshot> {
  UUID id;
  long expectedVersion;
}
