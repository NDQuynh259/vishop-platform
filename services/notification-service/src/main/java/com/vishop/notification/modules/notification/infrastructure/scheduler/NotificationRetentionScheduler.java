package com.vishop.notification.modules.notification.infrastructure.scheduler;

import com.vishop.notification.modules.notification.application.service.NotificationRetentionService;
import com.vishop.notification.modules.notification.infrastructure.config.NotificationRetentionProperties;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

/** Deletes only recipient state; shared payloads, source idempotency and deliveries stay intact. */
@RequiredArgsConstructor 
public class NotificationRetentionScheduler {
  private static final Logger log = LoggerFactory.getLogger(NotificationRetentionScheduler.class);
  private final NotificationRetentionService service;
  private final NotificationRetentionProperties properties;

  @Scheduled(
      fixedDelayString = "${notification.retention.fixed-delay-ms:300000}",
      initialDelayString = "${notification.retention.initial-delay-ms:60000}")
  public void purgeExpiredRecipients() {
    try {
      var result = service.purge(properties.toPolicy());
      if (result.deletedCount() > 0) {
        log.info(
            "Notification retention deleted {} recipients in {} batches",
            result.deletedCount(),
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