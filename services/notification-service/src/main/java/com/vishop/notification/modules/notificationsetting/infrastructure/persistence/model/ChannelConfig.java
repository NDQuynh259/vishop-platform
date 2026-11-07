package com.vishop.notification.modules.notificationsetting.infrastructure.persistence.model;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.vishop.notification.modules.setting.presentation.rest.dto.NotificationSettingReq.EmailConfig;
import com.vishop.notification.modules.setting.presentation.rest.dto.NotificationSettingReq.InAppConfig;
import com.vishop.notification.modules.setting.presentation.rest.dto.NotificationSettingReq.LarkConfig;
import com.vishop.notification.modules.setting.presentation.rest.dto.NotificationSettingReq.TelegramConfig;
import com.vishop.notification.shared.contract.NotificationChannel;

/**
 * Polymorphic channel configuration hierarchy. Each channel type is represented by a distinct
 * object.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type", defaultImpl = InAppConfig.class)
@JsonSubTypes({
    @JsonSubTypes.Type(value = InAppConfig.class, name = "IN_APP"),
    @JsonSubTypes.Type(value = TelegramConfig.class, name = "TELEGRAM"),
    @JsonSubTypes.Type(value = EmailConfig.class, name = "EMAIL"),
    @JsonSubTypes.Type(value = LarkConfig.class, name = "LAOOK")
})
@JsonDeserialize(using = ChannelConfigDeserializer.class)
public sealed interface ChannelConfig
    permits InAppConfig, TelegramConfig, EmailConfig, LarkConfig {

  String type();

  NotificationChannel channel();
}
