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
    var enabledChannels =
        settings.channels(tenantId).stream()
            .filter(c -> c.enabled() && c.available() && c.connectionVerified())
            .map(NotificationSettingManagementApi.ChannelView::channel)
            .collect(Collectors.toSet());
    var allowed =
        rule.getChannels().stream()
            .filter(enabledChannels::contains)
            .collect(Collectors.toUnmodifiableSet());
    return new RoutingPolicy(allowed, Set.of());
  }

  public Set<NotificationChannel> restrictForUser(
      UUID tenantId, UUID userId, String type, Set<NotificationChannel> tenantChannels) {
    return Set.copyOf(tenantChannels);
  }
}
