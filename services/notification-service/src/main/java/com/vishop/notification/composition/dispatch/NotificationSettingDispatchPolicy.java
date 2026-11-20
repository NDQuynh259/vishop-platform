package com.vishop.notification.composition.dispatch;

import com.vishop.notification.modules.setting.application.api.NotificationAlertRuleApi;
import com.vishop.notification.modules.setting.application.api.NotificationSettingManagementApi;
import com.vishop.notification.shared.constants.NotificationTypeCode;
import com.vishop.notification.shared.contract.NotificationChannel;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Resolves type rules and channel connections; recipient authorization belongs to
 * RecipientDirectory.
 */
@Component
@RequiredArgsConstructor
public class NotificationSettingDispatchPolicy implements NotificationDispatchPolicy {
  private final NotificationSettingManagementApi settings;
  private final NotificationAlertRuleApi rules;

  public RoutingPolicy resolve(UUID tenantId, TypePolicy type) {
    if (!type.available()) return new RoutingPolicy(Set.of(), Set.of());
    var code =
        NotificationTypeCode.fromCode(type.code())
            .orElseThrow(() -> new IllegalArgumentException("Unknown notification type"));
    var rule = rules.ruleForType(tenantId, code);
    if (!rule.isEnabled()) return new RoutingPolicy(Set.of(), Set.of());
    var ready =
        settings.channels(tenantId).stream()
            .filter(c -> rule.getId() != null || type.defaultChannels().contains(c.channel()))
            .filter(c -> c.enabled() && c.available() && c.connectionVerified())
            .filter(
                c ->
                    !c.channel().isGroupChannel()
                        || (c.id() != null && c.allowedTypes().contains(code)))
            .toList();
    var channels =
        ready.stream()
            .map(NotificationSettingManagementApi.ChannelView::channel)
            .collect(Collectors.toSet());
    var groups =
        ready.stream()
            .filter(c -> c.channel().isGroupChannel())
            .map(c -> new GroupSetting(c.channel(), c.id()))
            .collect(Collectors.toSet());
    return new RoutingPolicy(channels, Set.of(), groups);
  }

  public Set<NotificationChannel> restrictForUser(
      UUID tenantId, UUID userId, String type, Set<NotificationChannel> tenantChannels) {
    return Set.copyOf(tenantChannels);
  }
}
