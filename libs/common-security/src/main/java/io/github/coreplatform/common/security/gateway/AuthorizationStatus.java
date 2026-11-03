package io.github.coreplatform.common.security.gateway;

/**
 * Status vocabulary of the IAM authorization projection shared with the gateway. Domain entities
 * retain their own lifecycle types; these values describe cache snapshots.
 */
public enum AuthorizationStatus {
  ACTIVE,
  INACTIVE,
  SUSPENDED,
  ARCHIVED,
  REVOKED,
  EXPIRED
}
