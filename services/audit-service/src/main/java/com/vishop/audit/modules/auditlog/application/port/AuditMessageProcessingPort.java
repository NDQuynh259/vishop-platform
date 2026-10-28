package com.vishop.audit.modules.auditlog.application.port;

import java.util.UUID;

/** Runs the action and records consumption atomically, skipping an already processed event. */
public interface AuditMessageProcessingPort {
  boolean executeOnce(UUID eventId, String eventType, Runnable action);
}
