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
  void boundsTotalWorkAndAlternatesScopesEvenWithAnUnlimitedBacklog() {
    var policy = new NotificationRetentionPolicy(Duration.ofDays(90), Duration.ofDays(30), 500, 4);
    var visible = Instant.parse("2026-08-20T12:00:00.123456Z");
    var archived = Instant.parse("2026-10-19T12:00:00.123456Z");
    when(repository.purgeVisibleBefore(visible, 500)).thenReturn(500);
    when(repository.purgeArchivedBefore(archived, 500)).thenReturn(500);
    var result = service.purge(policy);
    assertThat(result.visibleDeleted()).isEqualTo(1000);
    assertThat(result.archivedDeleted()).isEqualTo(1000);
    assertThat(result.totalDeleted()).isEqualTo(2000);
    assertThat(result.batchesExecuted()).isEqualTo(4);
    var ordered = inOrder(repository);
    ordered.verify(repository).purgeArchivedBefore(archived, 500);
    ordered.verify(repository).purgeVisibleBefore(visible, 500);
    ordered.verify(repository).purgeArchivedBefore(archived, 500);
    ordered.verify(repository).purgeVisibleBefore(visible, 500);
    ordered.verifyNoMoreInteractions();
  }

  @Test
  void stopsExhaustedScopeAndGivesRemainingBudgetToTheOther() {
    when(repository.purgeArchivedBefore(any(), eq(2))).thenReturn(1);
    when(repository.purgeVisibleBefore(any(), eq(2))).thenReturn(2, 2, 0);
    var result =
        service.purge(
            new NotificationRetentionPolicy(Duration.ofDays(90), Duration.ofDays(90), 2, 10));
    assertThat(result.totalDeleted()).isEqualTo(5);
    assertThat(result.batchesExecuted()).isEqualTo(4);
    verify(repository, times(1)).purgeArchivedBefore(any(), eq(2));
    verify(repository, times(3)).purgeVisibleBefore(any(), eq(2));
  }

  @Test
  void stopsAfterFailureSoTheJobCanRetryRemainingWorkOnTheNextRun() {
    when(repository.purgeArchivedBefore(any(), anyInt())).thenReturn(500);
    when(repository.purgeVisibleBefore(any(), anyInt()))
        .thenThrow(new IllegalStateException("database unavailable"));
    assertThatThrownBy(
            () ->
                service.purge(
                    new NotificationRetentionPolicy(
                        Duration.ofDays(90), Duration.ofDays(90), 500, 10)))
        .isInstanceOf(IllegalStateException.class);
    verify(repository, times(1)).purgeArchivedBefore(any(), anyInt());
    verify(repository, times(1)).purgeVisibleBefore(any(), anyInt());
    verifyNoMoreInteractions(repository);
  }
}
