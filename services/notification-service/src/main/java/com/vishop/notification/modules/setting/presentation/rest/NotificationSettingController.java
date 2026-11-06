package com.vishop.notification.modules.setting.presentation.rest;

import com.vishop.notification.modules.setting.application.api.NotificationSettingManagementApi;
import com.vishop.notification.modules.setting.application.api.NotificationSettingManagementApi.*;
import com.vishop.notification.shared.contract.NotificationChannel;
import io.github.coreplatform.common.domain.constants.ApiPaths;
import io.github.coreplatform.common.domain.exception.BusinessRuleViolationException;
import io.github.coreplatform.common.security.gateway.GatewayPrincipal;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(ApiPaths.V1 + "/notification-settings")
public class NotificationSettingController {
  private final NotificationSettingManagementApi settings;

  public NotificationSettingController(NotificationSettingManagementApi settings) {
    this.settings = settings;
  }

  @GetMapping("/channels")
  public List<ChannelView> channels(@AuthenticationPrincipal GatewayPrincipal actor) {
    return settings.channels(
        NotificationSettingAccess.tenant(actor, NotificationSettingAccess.VIEW));
  }

  @GetMapping("/channels/{channel}")
  public ChannelView channel(
      @AuthenticationPrincipal GatewayPrincipal actor,
      @PathVariable("channel") NotificationChannel channel) {
    return settings.channel(
        NotificationSettingAccess.tenant(actor, NotificationSettingAccess.VIEW), channel);
  }

  @PutMapping("/channels/{channel}")
  public ChannelView changeChannel(
      @AuthenticationPrincipal GatewayPrincipal actor,
      @PathVariable("channel") NotificationChannel channel,
      @Valid @RequestBody NotificationSettingRequests.Channel body) {
    var tenant = NotificationSettingAccess.tenant(actor, NotificationSettingAccess.MANAGE);
    if (body.connectionConfig() != null) {
      if (body.botToken() != null || body.chatId() != null || body.messageThreadId() != null) {
        throw new BusinessRuleViolationException(
            "Use connectionConfig without legacy Telegram fields");
      }
      return settings.configureChannel(
          tenant,
          channel,
          new ConfigurationChange(body.enabled(), body.connectionConfig(), body.version()));
    }
    return settings.changeChannel(
        tenant,
        channel,
        new ChannelChange(
            body.enabled(),
            body.botToken(),
            body.chatId(),
            body.messageThreadId(),
            body.version()));
  }
}
