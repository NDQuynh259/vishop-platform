package com.vishop.notification.modules.notification.infrastructure.maintenance;

import com.vishop.notification.modules.notification.application.service.NotificationRetentionService;
import com.vishop.notification.modules.notification.infrastructure.config.NotificationRetentionProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

/** Deletes only recipient state; shared payloads, source idempotency and deliveries stay intact. */
public class NotificationRetentionJob {
  private static final Logger log = LoggerFactory.getLogger(NotificationRetentionJob.class);
  private final NotificationRetentionService service;
  private final NotificationRetentionProperties properties;

  public NotificationRetentionJob(
      NotificationRetentionService service, NotificationRetentionProperties properties) {
    this.service = service;
    this.properties = properties;
  }

  @Scheduled(
      fixedDelayString = "${notification.retention.fixed-delay-ms:300000}",
      initialDelayString = "${notification.retention.initial-delay-ms:60000}")
  public void purgeExpiredRecipients() {
    try {
      var result = service.purge(properties.toPolicy());
      if (result.totalDeleted() > 0) {
        log.info(
            "Notification retention deleted {} visible and {} archived recipients in {} batches",
            result.visibleDeleted(),
            result.archivedDeleted(),
            result.batchesExecuted());
      }
    } catch (RuntimeException failure) {
      // A failed batch rolls back itself. Previously committed batches stay deleted; next run
      // resumes.
      log.error(
          "Notification recipient retention failed; remaining rows will be retried on the next run",
          failure);
    }
  }
}
