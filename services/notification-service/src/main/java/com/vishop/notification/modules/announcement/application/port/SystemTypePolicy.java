package com.vishop.notification.modules.announcement.application.port;

/** Composition adapter checks catalog availability and SYSTEM category through CatalogApi. */
public interface SystemTypePolicy {
  void requirePublishableSystemType(String type);
}
