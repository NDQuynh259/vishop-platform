package com.vishop.notification.modules.notification.application.service;

import com.vishop.notification.modules.notification.application.dto.NotificationRetentionResult;
import com.vishop.notification.modules.notification.domain.port.NotificationRecipientRetentionRepository;
import com.vishop.notification.modules.notification.domain.valueobject.NotificationRetentionPolicy;
import java.time.Clock;
import java.time.temporal.ChronoUnit;

public class NotificationRetentionService {
  private final NotificationRecipientRetentionRepository repository;
  private final Clock clock;

  public NotificationRetentionService(
      NotificationRecipientRetentionRepository repository, Clock clock) {
    this.repository = repository;
    this.clock = clock;
  }

  public NotificationRetentionResult purge(NotificationRetentionPolicy policy) {
    // One fixed cutoff for the entire run; the repository commits each batch independently.
    var now = clock.instant();
    var visibleCutoff = now.minus(policy.visibleRetention()).truncatedTo(ChronoUnit.MICROS);
    var archivedCutoff = now.minus(policy.archivedRetention()).truncatedTo(ChronoUnit.MICROS);
    int visibleDeleted = 0, archivedDeleted = 0, batches = 0;
    boolean visibleDone = false, archivedDone = false;
    while (batches < policy.maxBatchesPerRun() && !(visibleDone && archivedDone)) {
      // Alternate scopes so a large archived backlog cannot starve visible (including unread)
      // expiry.
      boolean archived = !archivedDone && (visibleDone || batches % 2 == 0);
      int deleted =
          archived
              ? repository.purgeArchivedBefore(archivedCutoff, policy.batchSize())
              : repository.purgeVisibleBefore(visibleCutoff, policy.batchSize());
      if (archived) {
        archivedDeleted += deleted;
        archivedDone = deleted < policy.batchSize();
      } else {
        visibleDeleted += deleted;
        visibleDone = deleted < policy.batchSize();
      }
      batches++;
    }
    return new NotificationRetentionResult(visibleDeleted, archivedDeleted, batches);
  }
}
