package com.vishop.notification.modules.delivery.domain.entity;

import com.vishop.notification.shared.contract.NotificationChannel;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record DeliveryJob(
    UUID id,
    UUID tenantId,
    UUID notificationId,
    UUID userId,
    NotificationChannel channel,
    String destination,
    Status status,
    int attemptCount,
    int maxAttempts,
    Instant nextAttemptAt,
    UUID lockToken,
    Instant lockedUntil,
    Instant sentAt) {
  public enum Status {
    PENDING,
    PROCESSING,
    SENT,
    FAILED,
    CANCELLED
  }

  public DeliveryJob {
    Objects.requireNonNull(id);
    Objects.requireNonNull(tenantId);
    Objects.requireNonNull(notificationId);
    Objects.requireNonNull(channel);
    Objects.requireNonNull(status);
    Objects.requireNonNull(nextAttemptAt);
    if (channel == NotificationChannel.IN_APP || destination == null || destination.isBlank())
      throw new IllegalArgumentException("Provider destination required");
    if (maxAttempts <= 0 || attemptCount < 0 || attemptCount > maxAttempts)
      throw new IllegalArgumentException("Invalid attempt budget");
    if ((lockToken == null) != (lockedUntil == null)
        || ((status == Status.PROCESSING) != (lockToken != null))
        || ((status == Status.SENT) != (sentAt != null)))
      throw new IllegalArgumentException("Invalid delivery state");
  }

  public static DeliveryJob pending(
      UUID id,
      UUID tenant,
      UUID notification,
      UUID user,
      NotificationChannel channel,
      String destination,
      Instant now) {
    return new DeliveryJob(
        id,
        tenant,
        notification,
        user,
        channel,
        destination,
        Status.PENDING,
        0,
        5,
        now,
        null,
        null,
        null);
  }

  public DeliveryJob claim(UUID token, Instant now, Duration lease) {
    Objects.requireNonNull(token);
    Objects.requireNonNull(now);
    Objects.requireNonNull(lease);
    if (status != Status.PENDING || now.isBefore(nextAttemptAt) || attemptCount >= maxAttempts)
      throw new IllegalStateException("Job cannot be claimed");
    if (lease.isNegative() || lease.isZero())
      throw new IllegalArgumentException("Positive lease required");
    return new DeliveryJob(
        id,
        tenantId,
        notificationId,
        userId,
        channel,
        destination,
        Status.PROCESSING,
        attemptCount + 1,
        maxAttempts,
        nextAttemptAt,
        token,
        now.plus(lease),
        null);
  }

  public DeliveryJob complete(UUID token, Instant now) {
    requireLease(token, now);
    return new DeliveryJob(
        id,
        tenantId,
        notificationId,
        userId,
        channel,
        destination,
        Status.SENT,
        attemptCount,
        maxAttempts,
        nextAttemptAt,
        null,
        null,
        now);
  }

  public DeliveryJob fail(UUID token, Instant now, Instant retryAt) {
    requireLease(token, now);
    boolean retry = retryAt != null && attemptCount < maxAttempts;
    if (retry && retryAt.isBefore(now))
      throw new IllegalArgumentException("Retry cannot be in the past");
    return new DeliveryJob(
        id,
        tenantId,
        notificationId,
        userId,
        channel,
        destination,
        retry ? Status.PENDING : Status.FAILED,
        attemptCount,
        maxAttempts,
        retry ? retryAt : nextAttemptAt,
        null,
        null,
        null);
  }

  private void requireLease(UUID token, Instant now) {
    Objects.requireNonNull(now);
    if (status != Status.PROCESSING || !lockToken.equals(token) || !now.isBefore(lockedUntil))
      throw new IllegalStateException("Stale or expired worker lease");
  }
}
