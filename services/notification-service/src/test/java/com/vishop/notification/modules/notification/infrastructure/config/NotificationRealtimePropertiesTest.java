package com.vishop.notification.modules.notification.infrastructure.config;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.vishop.notification.modules.notification.infrastructure.realtime.NotificationRedisRelay;
import com.vishop.notification.modules.notification.presentation.realtime.NotificationSseStreams;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

class NotificationRealtimePropertiesTest {
  final ApplicationContextRunner runner =
      new ApplicationContextRunner().withUserConfiguration(PropertiesConfiguration.class);

  @Configuration(proxyBeanMethods = false)
  @EnableConfigurationProperties(NotificationRealtimeProperties.class)
  static class PropertiesConfiguration {}

  @Test
  void defaultsUseRedisAndCanBeOverriddenPerEnvironment() {
    runner.run(
        context ->
            assertThat(context.getBean(NotificationRealtimeProperties.class))
                .isEqualTo(new NotificationRealtimeProperties()));
    runner
        .withPropertyValues(
            "notification.realtime.channel=prod:notification:v1",
            "notification.realtime.publisher-queue-capacity=8")
        .run(
            context -> {
              var properties = context.getBean(NotificationRealtimeProperties.class);
              assertThat(properties.channel()).isEqualTo("prod:notification:v1");
              assertThat(properties.publisherQueueCapacity()).isEqualTo(8);
            });
  }

  @Test
  void invalidTransportChannelOrLimitsFailAtStartup() {
    for (String value :
        new String[] {
          "transport=unknown",
          "channel= ",
          "publisher-queue-capacity=0",
          "listener-queue-capacity=0",
          "max-message-bytes=0"
        }) {
      runner
          .withPropertyValues("notification.realtime." + value)
          .run(context -> assertThat(context).hasFailed());
    }
  }

  @Test
  void explicitLocalModeDoesNotCreateRedisInfrastructure() {
    new ApplicationContextRunner()
        .withUserConfiguration(NotificationRealtimeConfiguration.class)
        .withBean(NotificationSseStreams.class, () -> mock(NotificationSseStreams.class))
        .withPropertyValues("notification.realtime.transport=local")
        .run(
            context ->
                assertThat(context)
                    .hasSingleBean(NotificationRealtimeConfiguration.LocalSignals.class)
                    .doesNotHaveBean(NotificationRedisRelay.class)
                    .doesNotHaveBean(RedisMessageListenerContainer.class));
  }

  @Test
  void saturatedListenerQueueRequestsRefreshWithoutBlockingOrAccumulatingMoreTasks()
      throws Exception {
    var streams = mock(NotificationSseStreams.class);
    var properties =
        new NotificationRealtimeProperties(
            NotificationRealtimeProperties.Transport.REDIS, "test:notification", 1, 1, 1024);
    var executor =
        new NotificationRealtimeConfiguration.RedisSignals()
            .notificationRedisDispatch(properties, streams);
    var entered = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    executor.initialize();
    try {
      executor.execute(
          () -> {
            entered.countDown();
            try {
              release.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException interrupted) {
              Thread.currentThread().interrupt();
            }
          });
      assertThat(entered.await(2, TimeUnit.SECONDS)).isTrue();
      executor.execute(() -> {});
      assertThatCode(() -> executor.execute(() -> {})).doesNotThrowAnyException();
      verify(streams).requestRefreshLocal();
      assertThat(executor.getThreadPoolExecutor().getQueue()).hasSize(1);
    } finally {
      release.countDown();
      executor.shutdown();
    }
  }
}
