package com.vishop.notification.shared.contract;

public enum NotificationChannel {
  IN_APP,
  EMAIL,
  TELEGRAM,
  LARK;

  public boolean isGroupChannel() {
    return this == LARK || this == TELEGRAM;
  }
}
