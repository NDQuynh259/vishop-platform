package com.vishop.notification.modules.notification.infrastructure.config;

import com.vishop.notification.modules.notification.application.command.ArchiveNotificationCommandHandler;
import com.vishop.notification.modules.notification.application.command.CreateNotificationCommandHandler;
import com.vishop.notification.modules.notification.application.command.EnqueueDeliveriesCommandHandler;
import com.vishop.notification.modules.notification.application.command.MarkAllNotificationsReadCommandHandler;
import com.vishop.notification.modules.notification.application.command.MarkNotificationReadCommandHandler;
import com.vishop.notification.modules.notification.application.port.NotificationInboxShopAccess;
import com.vishop.notification.modules.notification.application.query.GetNotificationQueryHandler;
import com.vishop.notification.modules.notification.application.query.ListInboxQueryHandler;
import com.vishop.notification.modules.notification.application.query.UnreadCountQueryHandler;
import com.vishop.notification.modules.notification.application.service.NotificationDeliveryService;
import com.vishop.notification.modules.notification.application.service.NotificationService;
import com.vishop.notification.modules.notification.domain.port.NotificationDeliveryRepository;
import com.vishop.notification.modules.notification.domain.port.NotificationRepository;
import com.vishop.notification.modules.notification.infrastructure.persistence.adapters.NotificationDeliveryJpaAdapter;
import com.vishop.notification.modules.notification.infrastructure.persistence.adapters.NotificationJpaAdapter;
import com.vishop.notification.modules.notification.infrastructure.persistence.entities.NotificationJpaEntity;
import com.vishop.notification.modules.notification.infrastructure.persistence.mapper.NotificationDeliveryPersistenceMapper;
import com.vishop.notification.modules.notification.infrastructure.persistence.repositories.SpringDataNotificationDeliveryRepository;
import com.vishop.notification.modules.setting.infrastructure.persistence.entities.NotificationChannelSettingJpaEntity;
import com.vishop.notification.shared.contract.NotificationChannel;
import io.github.coreplatform.common.domain.UuidV7;
import io.github.coreplatform.common.web.cqrs.CqrsAutoConfiguration;
import java.time.Clock;
import java.util.Set;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.*;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@Configuration(proxyBeanMethods = false)
@EntityScan(
    basePackageClasses = {NotificationJpaEntity.class, NotificationChannelSettingJpaEntity.class})
@EnableJpaRepositories(basePackageClasses = SpringDataNotificationDeliveryRepository.class)
@Import({
  NotificationJpaAdapter.class,
  NotificationDeliveryJpaAdapter.class,
  NotificationDeliveryPersistenceMapper.class,
  EnqueueDeliveriesCommandHandler.class,
  CqrsAutoConfiguration.class,
  CreateNotificationCommandHandler.class,
  ListInboxQueryHandler.class,
  GetNotificationQueryHandler.class,
  UnreadCountQueryHandler.class,
  MarkNotificationReadCommandHandler.class,
  MarkAllNotificationsReadCommandHandler.class,
  ArchiveNotificationCommandHandler.class
})
public class NotificationModuleConfiguration {
  @Bean
  NotificationDeliveryService notificationDeliveryService(
      NotificationDeliveryRepository repository, Clock clock) {
    return new NotificationDeliveryService(
        repository,
        clock,
        UuidV7::nextId,
        Set.of(NotificationChannel.EMAIL, NotificationChannel.TELEGRAM, NotificationChannel.LARK));
  }

  @Bean
  NotificationService notificationService(
      NotificationRepository repository, Clock clock, NotificationInboxShopAccess shops) {
    return new NotificationService(repository, clock, UuidV7::nextId, shops);
  }

  @Bean
  @ConditionalOnMissingBean(Clock.class)
  Clock notificationClock() {
    return Clock.systemUTC();
  }

  @Bean
  @ConditionalOnMissingBean(NotificationInboxShopAccess.class)
  NotificationInboxShopAccess notificationInboxShopAccess() {
    return (tenant, user) -> Set.of();
  }
}
