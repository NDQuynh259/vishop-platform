package com.vishop.notification.modules.inbox.infrastructure.config;

import com.vishop.notification.modules.inbox.application.command.ArchiveNotificationCommandHandler;
import com.vishop.notification.modules.inbox.application.command.CreateNotificationCommandHandler;
import com.vishop.notification.modules.inbox.application.command.MarkAllNotificationsReadCommandHandler;
import com.vishop.notification.modules.inbox.application.command.MarkNotificationReadCommandHandler;
import com.vishop.notification.modules.inbox.application.port.NotificationInboxShopAccess;
import com.vishop.notification.modules.inbox.application.query.GetNotificationQueryHandler;
import com.vishop.notification.modules.inbox.application.query.ListInboxQueryHandler;
import com.vishop.notification.modules.inbox.application.query.UnreadCountQueryHandler;
import com.vishop.notification.modules.inbox.application.service.NotificationInboxApplicationService;
import com.vishop.notification.modules.inbox.domain.port.NotificationInboxRepository;
import com.vishop.notification.modules.inbox.infrastructure.persistence.NotificationInboxJpaAdapter;
import com.vishop.notification.modules.inbox.infrastructure.persistence.entities.NotificationJpaEntity;
import io.github.coreplatform.common.domain.UuidV7;
import io.github.coreplatform.common.web.cqrs.CqrsAutoConfiguration;
import java.time.Clock;
import java.util.Set;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.*;

@Configuration(proxyBeanMethods = false)
@EntityScan(basePackageClasses = NotificationJpaEntity.class)
@Import({
  NotificationInboxJpaAdapter.class,
  CqrsAutoConfiguration.class,
  CreateNotificationCommandHandler.class,
  ListInboxQueryHandler.class,
  GetNotificationQueryHandler.class,
  UnreadCountQueryHandler.class,
  MarkNotificationReadCommandHandler.class,
  MarkAllNotificationsReadCommandHandler.class,
  ArchiveNotificationCommandHandler.class
})
public class NotificationInboxModuleConfiguration {
  @Bean
  NotificationInboxApplicationService notificationInboxService(
      NotificationInboxRepository repository, Clock clock, NotificationInboxShopAccess shops) {
    return new NotificationInboxApplicationService(repository, clock, UuidV7::nextId, shops);
  }

  @Bean
  @ConditionalOnMissingBean(Clock.class)
  Clock notificationInboxClock() {
    return Clock.systemUTC();
  }

  @Bean
  @ConditionalOnMissingBean(NotificationInboxShopAccess.class)
  NotificationInboxShopAccess notificationInboxShopAccess() {
    return (tenant, user) -> Set.of();
  }
}
