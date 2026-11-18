package com.vishop.notification.modules.notification.infrastructure.realtime;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vishop.notification.modules.notification.application.dto.NotificationCreated;
import com.vishop.notification.modules.notification.infrastructure.config.NotificationRealtimeProperties;
import com.vishop.notification.modules.notification.presentation.realtime.NotificationSseStreams;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.core.StringRedisTemplate;

class NotificationRedisRelayTest {
  final ObjectMapper json = new ObjectMapper();
  final StringRedisTemplate redis = mock(StringRedisTemplate.class);
  final NotificationSseStreams streams = mock(NotificationSseStreams.class);
  final NotificationRealtimeProperties properties = new NotificationRealtimeProperties();
  final ExecutorService publisher = mock(ExecutorService.class);
  final NotificationCreated event =
      new NotificationCreated(
          UUID.randomUUID(), UUID.randomUUID(), Set.of(UUID.randomUUID()), "inventory.view");

  @Test
  void publicationIsOffloadedAndOnlyRedisReceptionBroadcastsLocally() throws Exception {
    var jobs = new ArrayDeque<Runnable>();
    doAnswer(
            invocation -> {
              jobs.add(invocation.getArgument(0));
              return null;
            })
        .when(publisher)
        .execute(any(Runnable.class));
    try (var relay = new NotificationRedisRelay(redis, json, streams, properties, publisher)) {
      relay.publishAfterCommit(event);
      verifyNoInteractions(redis, streams);
      jobs.remove().run();
      var payload = org.mockito.ArgumentCaptor.forClass(String.class);
      verify(redis).convertAndSend(eq(properties.channel()), payload.capture());
      assertThat(json.readValue(payload.getValue(), NotificationCreated.class)).isEqualTo(event);
      verifyNoInteractions(streams);
      relay.onMessage(message(properties.channel(), payload.getValue()), null);
      verify(streams).broadcastLocal(event);
    }
  }

  @Test
  void queueOverflowDoesNotEscapeIntoTheAlreadyCommittedRequest() {
    doThrow(new RejectedExecutionException("Queue full")).when(publisher).execute(any());
    try (var relay = new NotificationRedisRelay(redis, json, streams, properties, publisher)) {
      assertThatCode(() -> relay.publishAfterCommit(event)).doesNotThrowAnyException();
      verifyNoInteractions(redis);
      verify(streams).requestRefreshLocal();
    }
  }

  @Test
  void redisFailureDoesNotFailCommittedWorkOrDirectlyDeliverTheEvent() {
    doAnswer(
            invocation -> {
              invocation.<Runnable>getArgument(0).run();
              return null;
            })
        .when(publisher)
        .execute(any());
    when(redis.convertAndSend(anyString(), anyString()))
        .thenThrow(new RedisConnectionFailureException("Unavailable"));
    try (var relay = new NotificationRedisRelay(redis, json, streams, properties, publisher)) {
      assertThatCode(() -> relay.publishAfterCommit(event)).doesNotThrowAnyException();
      verify(streams).requestRefreshLocal();
      verify(streams, never()).broadcastLocal(any());
    }
  }

  @Test
  void malformedOversizedAndIncompleteMessagesAreDiscardedWithoutBreakingTheListener()
      throws Exception {
    var small =
        new NotificationRealtimeProperties(
            NotificationRealtimeProperties.Transport.REDIS, properties.channel(), 2, 2, 1024);
    try (var relay = new NotificationRedisRelay(redis, json, streams, small, publisher)) {
      relay.onMessage(message(small.channel(), "not-json"), null);
      relay.onMessage(message(small.channel(), "x".repeat(1025)), null);
      relay.onMessage(message(small.channel(), "{}"), null);
      verify(streams, times(3)).requestRefreshLocal();
      verify(streams, never()).broadcastLocal(any());
      relay.onMessage(message(small.channel(), json.writeValueAsString(event)), null);
      verify(streams).broadcastLocal(event);
    }
  }

  @Test
  void onlyTheConfiguredChannelCanDeliverOrRequestReconciliation() throws Exception {
    try (var relay = new NotificationRedisRelay(redis, json, streams, properties, publisher)) {
      relay.onMessage(message("another-environment", json.writeValueAsString(event)), null);
      relay.onChannelSubscribed("another-environment".getBytes(StandardCharsets.UTF_8), 1);
      verifyNoInteractions(streams);
      relay.onChannelSubscribed(properties.channel().getBytes(StandardCharsets.UTF_8), 1);
      verify(streams).requestRefreshLocal();
    }
  }

  private Message message(String channel, String payload) {
    var message = mock(Message.class);
    when(message.getBody()).thenReturn(payload.getBytes(StandardCharsets.UTF_8));
    when(message.getChannel()).thenReturn(channel.getBytes(StandardCharsets.UTF_8));
    return message;
  }
}
