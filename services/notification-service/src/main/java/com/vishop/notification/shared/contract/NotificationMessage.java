package com.vishop.notification.shared.contract;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Immutable integration data; identities/permissions must be checked by the dispatch boundary. */
public record NotificationMessage(
    UUID tenantId,
    String sourceEventId,
    String type,
    String subjectType,
    String subjectId,
    String requiredPermission,
    UUID shopId,
    Severity severity,
    String title,
    String body,
    String actionUrl,
    Instant occurredAt) {
  public enum Severity {
    INFO,
    WARNING,
    CRITICAL
  }

  public NotificationMessage {
    Objects.requireNonNull(tenantId);
    Objects.requireNonNull(severity);
    Objects.requireNonNull(occurredAt);
    sourceEventId = required(sourceEventId);
    type = required(type);
    subjectType = required(subjectType);
    subjectId = required(subjectId);
    requiredPermission = required(requiredPermission);
    title = required(title);
    Objects.requireNonNull(body);
  }

  private static String required(String value) {
    if (value == null || value.isBlank())
      throw new IllegalArgumentException("Required notification field");
    return value;
  }
}
