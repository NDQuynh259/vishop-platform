package com.vishop.audit.modules.auditlog.application.dto;

import java.util.Objects;
import java.util.UUID;

/** Identity supplied by the trusted authentication adapter, never from request parameters. */
public record CmsAuditActor(UUID userId, String systemRole) {
  public CmsAuditActor {
    Objects.requireNonNull(userId, "userId");
  }
}
