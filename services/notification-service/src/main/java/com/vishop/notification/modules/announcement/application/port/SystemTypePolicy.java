package com.vishop.notification.modules.announcement.application.port;

/** Composition adapter checks the supported type and SYSTEM category in shared constants. */
public interface SystemTypePolicy {
  void requirePublishableSystemType(String type);
}
