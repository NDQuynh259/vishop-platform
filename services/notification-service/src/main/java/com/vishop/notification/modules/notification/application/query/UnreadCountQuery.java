package com.vishop.notification.modules.notification.application.query;

import com.vishop.notification.modules.notification.application.dto.InboxActor;
import com.vishop.notification.modules.notification.application.dto.UnreadCount;
import io.github.coreplatform.common.domain.cqrs.Query;
import lombok.Value;

@Value
public class UnreadCountQuery implements Query<UnreadCount> {
  InboxActor actor;
}
