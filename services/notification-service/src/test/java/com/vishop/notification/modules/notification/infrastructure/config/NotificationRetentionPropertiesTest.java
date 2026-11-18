package com.vishop.notification.modules.notification.infrastructure.config;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.vishop.notification.modules.notification.application.service.NotificationRetentionService;
import com.vishop.notification.modules.notification.domain.port.NotificationRecipientRetentionRepository;
import com.vishop.notification.modules.notification.infrastructure.maintenance.NotificationRetentionJob;
import java.time.*;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class NotificationRetentionPropertiesTest {
  final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withUserConfiguration(NotificationRetentionConfiguration.class);

  @Test
  void deletionIsOptInAndDefaultsToNinetyDaysForBothScopes() {
    runner.run(
        context -> {
          assertThat(context)
              .doesNotHaveBean(NotificationRetentionJob.class)
              .doesNotHaveBean(NotificationRetentionService.class);
          var properties = context.getBean(NotificationRetentionProperties.class);
          assertThat(properties.enabled()).isFalse();
          assertThat(properties.visibleRetention()).isEqualTo(Duration.ofDays(90));
          assertThat(properties.archivedRetention()).isEqualTo(Duration.ofDays(90));
          assertThat(properties.batchSize()).isEqualTo(500);
          assertThat(properties.maxBatchesPerRun()).isEqualTo(10);
        });
  }

  @Test
  void enabledJobUsesConfiguredPolicyAndSurvivesAFailedRun() {
    var repository = mock(NotificationRecipientRetentionRepository.class);
    var now = Instant.parse("2026-11-18T00:00:00Z");
    when(repository.purgeArchivedBefore(any(), anyInt()))
        .thenThrow(new IllegalStateException("temporary outage"))
        .thenReturn(0);
    runner
        .withBean(NotificationRecipientRetentionRepository.class, () -> repository)
        .withBean(Clock.class, () -> Clock.fixed(now, ZoneOffset.UTC))
        .withPropertyValues(
            "notification.retention.enabled=true",
            "notification.retention.visible-retention=60d",
            "notification.retention.archived-retention=30d",
            "notification.retention.batch-size=20",
            "notification.retention.initial-delay-ms=86400000")
        .run(
            context -> {
              assertThat(context)
                  .hasSingleBean(NotificationRetentionJob.class)
                  .hasSingleBean(NotificationRetentionService.class);
              var job = context.getBean(NotificationRetentionJob.class);
              assertThatCode(job::purgeExpiredRecipients).doesNotThrowAnyException();
              job.purgeExpiredRecipients();
              verify(repository, times(2)).purgeArchivedBefore(now.minus(Duration.ofDays(30)), 20);
              verify(repository).purgeVisibleBefore(now.minus(Duration.ofDays(60)), 20);
            });
  }

  @Test
  void invalidRetentionOrUnboundedWorkFailsAtStartup() {
    for (String value :
        new String[] {
          "visible-retention=0d",
          "archived-retention=-1d",
          "batch-size=0",
          "batch-size=10001",
          "max-batches-per-run=1",
          "max-batches-per-run=101",
          "fixed-delay-ms=0",
          "initial-delay-ms=-1"
        }) {
      runner
          .withPropertyValues("notification.retention." + value)
          .run(context -> assertThat(context).hasFailed());
    }
  }
}
