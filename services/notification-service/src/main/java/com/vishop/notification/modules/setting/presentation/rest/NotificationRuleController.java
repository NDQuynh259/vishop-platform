package com.vishop.notification.modules.setting.presentation.rest;

import com.vishop.notification.modules.setting.application.api.NotificationAlertRuleApi.RuleChange;
import com.vishop.notification.modules.setting.application.api.NotificationAlertRuleApi.RuleView;
import com.vishop.notification.modules.setting.application.command.CreateRuleCommand;
import com.vishop.notification.modules.setting.application.command.UpdateRuleCommand;
import com.vishop.notification.modules.setting.application.query.GetRuleQuery;
import com.vishop.notification.modules.setting.application.query.ListRulesQuery;
import com.vishop.notification.modules.setting.presentation.rest.dto.NotificationRuleCreateReq;
import com.vishop.notification.modules.setting.presentation.rest.dto.NotificationRuleUpdateReq;
import io.github.coreplatform.common.domain.constants.ApiPaths;
import io.github.coreplatform.common.domain.cqrs.CommandBus;
import io.github.coreplatform.common.domain.cqrs.QueryBus;
import io.github.coreplatform.common.security.annotation.RequirePermission;
import io.github.coreplatform.common.security.constants.CommonPermissions.Notification;
import io.github.coreplatform.common.security.gateway.GatewayPrincipal;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(ApiPaths.V1 + "/notification-settings/rules")
@RequiredArgsConstructor
public class NotificationRuleController {
  private final CommandBus commands;
  private final QueryBus queries;

  @GetMapping
  @RequirePermission(Notification.SETTINGS_VIEW)
  public List<RuleView> rules(@AuthenticationPrincipal GatewayPrincipal actor) {
    return queries.dispatch(new ListRulesQuery(actor.tenantId()));
  }

  @GetMapping("/{ruleId}")
  @RequirePermission(Notification.SETTINGS_VIEW)
  public RuleView rule(@AuthenticationPrincipal GatewayPrincipal actor, @PathVariable UUID ruleId) {
    return queries.dispatch(new GetRuleQuery(actor.tenantId(), ruleId));
  }

  @PostMapping
  @RequirePermission(Notification.SETTINGS_MANAGE)
  public ResponseEntity<RuleView> createRule(
      @AuthenticationPrincipal GatewayPrincipal actor,
      @Valid @RequestBody NotificationRuleCreateReq body) {
    var created =
        commands.dispatch(
            new CreateRuleCommand(
                actor.tenantId(), body.getType(), body.getEnabled(), body.getChannels()));
    return ResponseEntity.created(
            URI.create(ApiPaths.V1 + "/notification-settings/rules/" + created.getId()))
        .body(created);
  }

  @PutMapping("/{ruleId}")
  @RequirePermission(Notification.SETTINGS_MANAGE)
  public RuleView updateRule(
      @AuthenticationPrincipal GatewayPrincipal actor,
      @PathVariable UUID ruleId,
      @Valid @RequestBody NotificationRuleUpdateReq body) {
    return commands.dispatch(
        new UpdateRuleCommand(
            actor.tenantId(),
            ruleId,
            new RuleChange(body.getEnabled(), body.getChannels(), body.getVersion())));
  }
}
