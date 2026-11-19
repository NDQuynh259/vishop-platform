package com.vishop.notification.modules.notification.application.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.vishop.notification.modules.notification.domain.port.NotificationRecipientRetentionRepository;
import com.vishop.notification.modules.notification.domain.valueobject.NotificationRetentionPolicy;
import java.time.*;
import org.junit.jupiter.api.Test;

class NotificationRetentionServiceTest {
  final NotificationRecipientRetentionRepository repository =
      mock(NotificationRecipientRetentionRepository.class);
  final Instant now = Instant.parse("2026-11-18T12:00:00.123456789Z");
  final NotificationRetentionService service =
      new NotificationRetentionService(repository, Clock.fixed(now, ZoneOffset.UTC));

  @Test
  void boundsTotalWorkEvenWithAnUnlimitedBacklog() {
    var policy = new NotificationRetentionPolicy(Duration.ofDays(90), 500, 4);
    var cutoff = Instant.parse("2026-08-20T12:00:00.123456Z");
    when(repository.purgeCreatedBefore(cutoff, 500)).thenReturn(500);
    var result = service.purge(policy);
    assertThat(result.deletedCount()).isEqualTo(2000);
    assertThat(result.batchesExecuted()).isEqualTo(4);
    verify(repository, times(4)).purgeCreatedBefore(cutoff, 500);
    verifyNoMoreInteractions(repository);
  }

  @Test
  void stopsEarlyOnceABatchDeletesFewerRowsThanBatchSize() {
    when(repository.purgeCreatedBefore(any(), eq(2))).thenReturn(2, 2, 1);
    var result = service.purge(new NotificationRetentionPolicy(Duration.ofDays(90), 2, 10));
    assertThat(result.deletedCount()).isEqualTo(5);
    assertThat(result.batchesExecuted()).isEqualTo(3);
    verify(repository, times(3)).purgeCreatedBefore(any(), eq(2));
    verifyNoMoreInteractions(repository);
  }

  @Test
  void stopsAfterFailureSoTheSchedulerCanRetryRemainingWorkOnTheNextRun() {
    when(repository.purgeCreatedBefore(any(), anyInt()))
        .thenReturn(500)
        .thenThrow(new IllegalStateException("database unavailable"));
    assertThatThrownBy(
            () -> service.purge(new NotificationRetentionPolicy(Duration.ofDays(90), 500, 10)))
        .isInstanceOf(IllegalStateException.class);
    verify(repository, times(2)).purgeCreatedBefore(any(), anyInt());
    verifyNoMoreInteractions(repository);
  }
}
