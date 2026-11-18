package com.vishop.notification.modules.notification.infrastructure.config;

import com.vishop.notification.modules.notification.application.service.NotificationRetentionService;
import com.vishop.notification.modules.notification.domain.port.NotificationRecipientRetentionRepository;
import com.vishop.notification.modules.notification.infrastructure.maintenance.NotificationRetentionJob;
import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.*;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(NotificationRetentionProperties.class)
@Import(NotificationRetentionConfiguration.Enabled.class)
public class NotificationRetentionConfiguration {
  @Configuration(proxyBeanMethods = false)
  @ConditionalOnProperty(prefix = "notification.retention", name = "enabled", havingValue = "true")
  @EnableScheduling
  static class Enabled {
    @Bean
    NotificationRetentionService notificationRetentionService(
        NotificationRecipientRetentionRepository repository, Clock clock) {
      return new NotificationRetentionService(repository, clock);
    }

    @Bean
    NotificationRetentionJob notificationRetentionJob(
        NotificationRetentionService service, NotificationRetentionProperties properties) {
      return new NotificationRetentionJob(service, properties);
    }
  }
}
