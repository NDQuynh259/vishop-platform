package com.vishop.notification.modules.setting.presentation.rest;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vishop.notification.modules.setting.application.api.NotificationSettingManagementApi.*;
import com.vishop.notification.modules.setting.application.command.CreateChannelCommand;
import com.vishop.notification.modules.setting.application.command.UpdateChannelCommand;
import com.vishop.notification.modules.setting.application.query.GetChannelQuery;
import com.vishop.notification.modules.setting.application.query.ListChannelsQuery;
import com.vishop.notification.modules.setting.presentation.rest.dto.NotificationSettingCreateReq;
import com.vishop.notification.modules.setting.presentation.rest.dto.NotificationSettingUpdateReq;
import com.vishop.notification.shared.contract.NotificationChannel;
import io.github.coreplatform.common.domain.constants.ApiPaths;
import io.github.coreplatform.common.domain.cqrs.CommandBus;
import io.github.coreplatform.common.domain.cqrs.QueryBus;
import io.github.coreplatform.common.domain.exception.BadRequestException;
import io.github.coreplatform.common.domain.exception.BusinessRuleViolationException;
import io.github.coreplatform.common.security.annotation.RequirePermission;
import io.github.coreplatform.common.security.constants.CommonPermissions.Notification;
import io.github.coreplatform.common.security.gateway.GatewayPrincipal;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Valid;
import jakarta.validation.Validator;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(ApiPaths.V1 + "/notification-settings")
public class NotificationSettingController {
  private final CommandBus commands;
  private final QueryBus queries;
  private final ObjectMapper json;
  private final Validator validator;

  public NotificationSettingController(
      CommandBus commands, QueryBus queries, ObjectMapper json, Validator validator) {
    this.commands = commands;
    this.queries = queries;
    this.json = json;
    this.validator = validator;
  }

  @PostMapping("/channels")
  @RequirePermission(Notification.SETTINGS_MANAGE)
  public ResponseEntity<ChannelView> createChannel(
      @AuthenticationPrincipal GatewayPrincipal actor,
      @Valid @RequestBody NotificationSettingCreateReq body) {
    var config =
        body.getConnectionConfig() == null || body.getConnectionConfig().isNull()
            ? Map.<String, String>of()
            : connectionConfig(body.getChannel(), body.getConnectionConfig());
    var created =
        commands.dispatch(
            new CreateChannelCommand(
                actor.tenantId(), body.getChannel(), body.getName(), body.getEnabled(), config));
    return ResponseEntity.created(
            URI.create(ApiPaths.V1 + "/notification-settings/channels/" + created.id()))
        .body(created);
  }

  @GetMapping("/channels")
  @RequirePermission(Notification.SETTINGS_VIEW)
  public List<ChannelView> channels(@AuthenticationPrincipal GatewayPrincipal actor) {
    return queries.dispatch(new ListChannelsQuery(actor.tenantId()));
  }

  @GetMapping("/channels/{channelId}")
  @RequirePermission(Notification.SETTINGS_VIEW)
  public ChannelView channel(
      @AuthenticationPrincipal GatewayPrincipal actor, @PathVariable UUID channelId) {
    return queries.dispatch(new GetChannelQuery(actor.tenantId(), channelId));
  }

  @PutMapping("/channels/{channelId}")
  @RequirePermission(Notification.SETTINGS_MANAGE)
  public ChannelView updateChannel(
      @AuthenticationPrincipal GatewayPrincipal actor,
      @PathVariable UUID channelId,
      @Valid @RequestBody NotificationSettingUpdateReq body) {
    Map<String, String> config = null;
    if (body.getConnectionConfig() != null && !body.getConnectionConfig().isNull()) {
      if (body.getBotToken() != null
          || body.getChatId() != null
          || body.getMessageThreadId() != null)
        throw new BusinessRuleViolationException(
            "Use connectionConfig without legacy Telegram fields");
      var current = queries.dispatch(new GetChannelQuery(actor.tenantId(), channelId));
      config = connectionConfig(current.channel(), body.getConnectionConfig());
    }
    var change =
        new ChannelUpdate(
            body.getEnabled(),
            config,
            body.getVersion(),
            body.getName(),
            body.getBotToken(),
            body.getChatId(),
            body.getMessageThreadId());
    return commands.dispatch(new UpdateChannelCommand(actor.tenantId(), channelId, change));
  }

  private Map<String, String> connectionConfig(NotificationChannel channel, JsonNode config) {
    Class<? extends NotificationSettingUpdateReq.ConnectionConfig> type =
        switch (channel) {
          case IN_APP -> NotificationSettingUpdateReq.InAppConfig.class;
          case TELEGRAM -> NotificationSettingUpdateReq.TelegramConfig.class;
          case EMAIL -> NotificationSettingUpdateReq.EmailConfig.class;
          case LARK -> NotificationSettingUpdateReq.LarkConfig.class;
        };
    if (!config.isObject()) {
      throw new BadRequestException("connectionConfig must be an object");
    }
    NotificationSettingUpdateReq.ConnectionConfig connection;
    try {
      connection = json.treeToValue(config, type);
    } catch (JsonProcessingException ex) {
      throw new BusinessRuleViolationException("Invalid connectionConfig for " + channel);
    }
    var violations = validator.validate(connection);
    if (!violations.isEmpty()) throw new ConstraintViolationException(violations);
    return connection.toMap();
  }
}
