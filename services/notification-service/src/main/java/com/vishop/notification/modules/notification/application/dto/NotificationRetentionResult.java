package com.vishop.notification.modules.notification.application.dto;

public record NotificationRetentionResult(
    int visibleDeleted, int archivedDeleted, int batchesExecuted) {
  public int totalDeleted() {
    return visibleDeleted + archivedDeleted;
  }
}
