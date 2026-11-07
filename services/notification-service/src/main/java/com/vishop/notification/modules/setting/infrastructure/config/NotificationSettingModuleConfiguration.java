package com.vishop.notification.modules.setting.infrastructure.config;

import com.vishop.notification.modules.setting.application.api.NotificationSettingManagementApi;
import com.vishop.notification.modules.setting.application.command.CreateChannelCommandHandler;
import com.vishop.notification.modules.setting.application.command.UpdateChannelCommandHandler;
import com.vishop.notification.modules.setting.application.port.NotificationSettingConnectionPolicy;
import com.vishop.notification.modules.setting.application.query.GetChannelQueryHandler;
import com.vishop.notification.modules.setting.application.query.ListChannelsQueryHandler;
import com.vishop.notification.modules.setting.application.service.NotificationChannelSupport;
import com.vishop.notification.modules.setting.application.service.NotificationSettingService;
import com.vishop.notification.modules.setting.domain.entity.NotificationSettingState.Connection;
import com.vishop.notification.modules.setting.domain.port.NotificationSettingManagementRepository;
import com.vishop.notification.modules.setting.infrastructure.persistence.entities.NotificationChannelSettingJpaEntity;
import com.vishop.notification.modules.setting.infrastructure.persistence.mapper.NotificationSettingPersistenceMapper;
import com.vishop.notification.modules.setting.infrastructure.persistence.repositories.SpringDataNotificationChannelSettingRepository;
import com.vishop.notification.shared.contract.NotificationChannel;
import io.github.coreplatform.common.domain.exception.BusinessRuleViolationException;
import io.github.coreplatform.common.web.cqrs.CqrsAutoConfiguration;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@Configuration(proxyBeanMethods = false)
@EntityScan(basePackageClasses = NotificationChannelSettingJpaEntity.class)
@EnableJpaRepositories(basePackageClasses = SpringDataNotificationChannelSettingRepository.class)
@Import({
  NotificationSettingPersistenceMapper.class,
  NotificationChannelSupport.class,
  CqrsAutoConfiguration.class,
  UpdateChannelCommandHandler.class,
  CreateChannelCommandHandler.class,
  GetChannelQueryHandler.class,
  ListChannelsQueryHandler.class
})
public class NotificationSettingModuleConfiguration {
  @Bean
  NotificationSettingService notificationSettingService(
      NotificationSettingManagementRepository repository,
      NotificationSettingConnectionPolicy connections,
      NotificationChannelSupport support) {
    return new NotificationSettingService(repository, connections, support);
  }

  @Bean
  @ConditionalOnMissingBean(NotificationSettingConnectionPolicy.class)
  NotificationSettingConnectionPolicy unavailableConnectionPolicy() {
    return new NotificationSettingConnectionPolicy() {
      public boolean available(NotificationChannel channel) {
        return channel == NotificationChannel.IN_APP;
      }

      public boolean ready(UUID tenant, Connection connection) {
        return available(connection.channel());
      }

      public Connection prepare(
          UUID tenant, Connection current, NotificationSettingManagementApi.ChannelUpdate change) {
        throw new BusinessRuleViolationException(
            "Provider verification and secret storage are not available yet");
      }
    };
  }
}
