package com.vishop.notification.modules.notification.infrastructure.config;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.vishop.notification.modules.notification.application.service.NotificationRetentionService;
import com.vishop.notification.modules.notification.domain.port.NotificationRecipientRetentionRepository;
import com.vishop.notification.modules.notification.infrastructure.scheduler.NotificationRetentionScheduler;
import java.time.*;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class NotificationRetentionPropertiesTest {
  final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withUserConfiguration(NotificationRetentionConfiguration.class);

  @Test
  void deletionIsOptInAndDefaultsToNinetyDays() {
    runner.run(
        context -> {
          assertThat(context)
              .doesNotHaveBean(NotificationRetentionScheduler.class)
              .doesNotHaveBean(NotificationRetentionService.class);
          var properties = context.getBean(NotificationRetentionProperties.class);
          assertThat(properties.enabled()).isFalse();
          assertThat(properties.period()).isEqualTo(Duration.ofDays(90));
          assertThat(properties.batchSize()).isEqualTo(500);
          assertThat(properties.maxBatchesPerRun()).isEqualTo(10);
        });
  }

  @Test
  void enabledSchedulerUsesConfiguredPolicyAndSurvivesAFailedRun() {
    var repository = mock(NotificationRecipientRetentionRepository.class);
    var now = Instant.parse("2026-11-18T00:00:00Z");
    when(repository.purgeCreatedBefore(any(), anyInt()))
        .thenThrow(new IllegalStateException("temporary outage"))
        .thenReturn(0);
    runner
        .withBean(NotificationRecipientRetentionRepository.class, () -> repository)
        .withBean(Clock.class, () -> Clock.fixed(now, ZoneOffset.UTC))
        .withPropertyValues(
            "notification.retention.enabled=true",
            "notification.retention.period=60d",
            "notification.retention.batch-size=20",
            "notification.retention.initial-delay-ms=86400000")
        .run(
            context -> {
              assertThat(context)
                  .hasSingleBean(NotificationRetentionScheduler.class)
                  .hasSingleBean(NotificationRetentionService.class);
              var scheduler = context.getBean(NotificationRetentionScheduler.class);
              assertThatCode(scheduler::purgeExpiredRecipients).doesNotThrowAnyException();
              scheduler.purgeExpiredRecipients();
              verify(repository, times(2)).purgeCreatedBefore(now.minus(Duration.ofDays(60)), 20);
            });
  }

  @Test
  void invalidRetentionOrUnboundedWorkFailsAtStartup() {
    for (String value :
        new String[] {
          "period=0d",
          "period=-1d",
          "batch-size=0",
          "batch-size=10001",
          "max-batches-per-run=0",
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
