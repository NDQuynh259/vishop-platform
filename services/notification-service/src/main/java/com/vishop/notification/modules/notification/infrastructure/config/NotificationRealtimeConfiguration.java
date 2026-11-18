package com.vishop.notification.modules.notification.infrastructure.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vishop.notification.modules.notification.application.dto.NotificationCreated;
import com.vishop.notification.modules.notification.infrastructure.realtime.NotificationRedisRelay;
import com.vishop.notification.modules.notification.presentation.realtime.NotificationSseStreams;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.event.TransactionalEventListener;

@Configuration(proxyBeanMethods = false)
@EnableTransactionManagement
@EnableConfigurationProperties(NotificationRealtimeProperties.class)
@Import(NotificationRealtimeConfiguration.RedisSignals.class)
public class NotificationRealtimeConfiguration {
  @Bean
  @ConditionalOnProperty(
      prefix = "notification.realtime",
      name = "transport",
      havingValue = "local")
  LocalSignals notificationLocalSignals(NotificationSseStreams streams) {
    return new LocalSignals(streams);
  }

  /** Explicit single-instance development/test mode; never combines with Redis forwarding. */
  public static final class LocalSignals {
    private final NotificationSseStreams streams;

    LocalSignals(NotificationSseStreams streams) {
      this.streams = streams;
    }

    @TransactionalEventListener
    public void afterCommit(NotificationCreated event) {
      streams.broadcastLocal(event);
    }
  }

  @Configuration(proxyBeanMethods = false)
  @ConditionalOnProperty(
      prefix = "notification.realtime",
      name = "transport",
      havingValue = "redis",
      matchIfMissing = true)
  static class RedisSignals {
    @Bean(destroyMethod = "close")
    NotificationRedisRelay notificationRedisRelay(
        StringRedisTemplate redis,
        ObjectMapper json,
        NotificationSseStreams streams,
        NotificationRealtimeProperties properties) {
      return new NotificationRedisRelay(redis, json, streams, properties);
    }

    @Bean
    ThreadPoolTaskExecutor notificationRedisDispatch(
        NotificationRealtimeProperties properties, NotificationSseStreams streams) {
      var executor = new ThreadPoolTaskExecutor();
      executor.setCorePoolSize(1);
      executor.setMaxPoolSize(1);
      executor.setQueueCapacity(properties.listenerQueueCapacity());
      executor.setThreadNamePrefix("notification-redis-listener-");
      executor.setDaemon(true);
      // Drop excess best-effort signals and coalesce a REST refresh on the SSE heartbeat tick.
      executor.setRejectedExecutionHandler(
          (task, pool) -> {
            if (!pool.isShutdown()) streams.requestRefreshLocal();
          });
      return executor;
    }

    @Bean
    RedisMessageListenerContainer notificationRedisListener(
        RedisConnectionFactory connectionFactory,
        NotificationRedisRelay relay,
        @Qualifier("notificationRedisDispatch") ThreadPoolTaskExecutor executor,
        NotificationRealtimeProperties properties) {
      var container = new RedisMessageListenerContainer();
      container.setConnectionFactory(connectionFactory);
      container.setTaskExecutor(executor);
      container.addMessageListener(relay, new ChannelTopic(properties.channel()));
      return container;
    }
  }
}
