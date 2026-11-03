package io.github.coreplatform.common.persistence;

/** Delivery lifecycle shared by services using the transactional outbox. */
public enum OutboxStatus {
  PENDING,
  PUBLISHED,
  FAILED
}
