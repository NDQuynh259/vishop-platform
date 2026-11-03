package com.vishop.iam.shared.application.port;

import java.util.UUID;

/** Confirms committed authorization changes in the gateway cache. */
public interface AuthorizationDeliveryPort {
  void deliverSession(UUID sessionId);

  void deliverMembership(UUID tenantId, UUID userId);

  class Unavailable extends RuntimeException {
    public Unavailable(Throwable cause) {
      super("Authorization cache is unavailable", cause);
    }
  }
}
