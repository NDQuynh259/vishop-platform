package com.vishop.notification.modules.notification.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.vishop.notification.modules.notification.presentation.realtime.NotificationSseSettings;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class NotificationSsePropertiesTest {
  final ApplicationContextRunner context =
      new ApplicationContextRunner().withUserConfiguration(PropertiesConfiguration.class);

  @Configuration(proxyBeanMethods = false)
  @EnableConfigurationProperties(NotificationSseProperties.class)
  static class PropertiesConfiguration {}

  @Test
  void defaultsAndPartialOverridesBindThroughSpring() {
    context.run(
        application -> {
          var properties = application.getBean(NotificationSseProperties.class);
          assertThat(properties).isEqualTo(new NotificationSseProperties());
          assertThat(properties.toSettings()).isEqualTo(new NotificationSseSettings());
        });
    context
        .withPropertyValues(
            "notification.sse.max-connections=250", "notification.sse.writer-threads=8")
        .run(
            application -> {
              var properties = application.getBean(NotificationSseProperties.class);
              assertThat(properties)
                  .isEqualTo(new NotificationSseProperties(250, 5, 32, 8, Duration.ofSeconds(15)));
              assertThat(properties.toSettings())
                  .isEqualTo(new NotificationSseSettings(250, 5, 32, 8, Duration.ofSeconds(15)));
            });
  }

  @Test
  void invalidLimitsFailAtStartup() {
    for (var property :
        new String[] {
          "max-connections=0",
          "max-connections-per-user=-1",
          "max-pending-events=0",
          "writer-threads=0",
          "heartbeat-interval=0ms",
          "heartbeat-interval=60s"
        }) {
      context
          .withPropertyValues("notification.sse." + property)
          .run(application -> assertThat(application).hasFailed());
    }
  }
}
