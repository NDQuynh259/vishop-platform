package com.vishop.notification.modules.notification.infrastructure.realtime;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vishop.notification.modules.notification.application.dto.NotificationCreated;
import com.vishop.notification.modules.notification.infrastructure.config.NotificationRealtimeProperties;
import com.vishop.notification.modules.notification.presentation.realtime.NotificationSseStreams;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.connection.SubscriptionListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** Best-effort signals between instances; only Redis reception broadcasts to local SSE clients. */
public class NotificationRedisRelay
    implements MessageListener, SubscriptionListener, AutoCloseable {
  private static final Logger log = LoggerFactory.getLogger(NotificationRedisRelay.class);
  private final StringRedisTemplate redis;
  private final ObjectMapper json;
  private final NotificationSseStreams streams;
  private final NotificationRealtimeProperties properties;
  private final byte[] channel;
  private final ExecutorService publisher;

  public NotificationRedisRelay(
      StringRedisTemplate redis,
      ObjectMapper json,
      NotificationSseStreams streams,
      NotificationRealtimeProperties properties) {
    this(
        redis,
        json,
        streams,
        properties,
        new ThreadPoolExecutor(
            1,
            1,
            0,
            TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(properties.publisherQueueCapacity()),
            Thread.ofPlatform().daemon().name("notification-redis-publisher").factory()));
  }

  NotificationRedisRelay(
      StringRedisTemplate redis,
      ObjectMapper json,
      NotificationSseStreams streams,
      NotificationRealtimeProperties properties,
      ExecutorService publisher) {
    this.redis = redis;
    this.json = json;
    this.streams = streams;
    this.properties = properties;
    this.channel = properties.channel().getBytes(StandardCharsets.UTF_8);
    this.publisher = publisher;
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void publishAfterCommit(NotificationCreated event) {
    try {
      // Redis I/O must not delay the transaction's caller after a successful database commit.
      publisher.execute(() -> publish(event));
    } catch (RejectedExecutionException ex) {
      log.warn(
          "Notification Redis publisher is full or stopped; signal dropped, REST remains authoritative");
      streams.requestRefreshLocal();
    }
  }

  private void publish(NotificationCreated event) {
    try {
      validate(event);
      var payload = json.writeValueAsString(event);
      if (payload.getBytes(StandardCharsets.UTF_8).length > properties.maxMessageBytes()) {
        throw new IllegalArgumentException(
            "Notification Redis message exceeds the configured limit");
      }
      redis.convertAndSend(properties.channel(), payload);
    } catch (JsonProcessingException | RuntimeException ex) {
      log.warn("Notification Redis publish failed; signal dropped, REST remains authoritative");
      log.debug("Notification Redis publish failure", ex);
      streams.requestRefreshLocal();
    }
  }

  @Override
  public void onMessage(Message message, byte[] pattern) {
    if (!Arrays.equals(channel, message.getChannel())) return;
    try {
      if (message.getBody().length > properties.maxMessageBytes()) {
        throw new IllegalArgumentException(
            "Notification Redis message exceeds the configured limit");
      }
      var event = json.readValue(message.getBody(), NotificationCreated.class);
      validate(event);
      streams.broadcastLocal(event);
    } catch (java.io.IOException | RuntimeException ex) {
      log.warn("Invalid notification Redis signal; requesting a local inbox refresh");
      log.debug("Notification Redis reception failure", ex);
      streams.requestRefreshLocal();
    }
  }

  @Override
  public void onChannelSubscribed(byte[] subscribedChannel, long count) {
    if (Arrays.equals(channel, subscribedChannel)) {
      // Redis may reconnect while HTTP SSE remains open; existing clients must reconcile via REST.
      streams.requestRefreshLocal();
    }
  }

  private static void validate(NotificationCreated event) {
    if (event == null
        || event.notificationId() == null
        || event.tenantId() == null
        || event.recipients() == null
        || event.recipients().isEmpty()
        || event.requiredPermission() == null
        || event.requiredPermission().isBlank()) {
      throw new IllegalArgumentException("Invalid notification Redis signal");
    }
  }

  @Override
  public void close() {
    publisher.shutdownNow();
  }
}
