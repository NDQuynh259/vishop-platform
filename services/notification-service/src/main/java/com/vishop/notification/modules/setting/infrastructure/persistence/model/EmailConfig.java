package com.vishop.notification.modules.setting.infrastructure.persistence.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record EmailConfig(
    String connectionRef, String credentialsRef, String fromAddress, String replyTo) {
  public EmailConfig(String connectionRef, String credentialsRef) {
    this(connectionRef, credentialsRef, null, null);
  }
}
