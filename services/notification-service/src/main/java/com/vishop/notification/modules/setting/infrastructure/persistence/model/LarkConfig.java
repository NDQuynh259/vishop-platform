package com.vishop.notification.modules.setting.infrastructure.persistence.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record LarkConfig(String webhookUrl, String appSecretRef, String appToken) {}
