package com.vishop.notification.composition;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.vishop.notification.composition.dispatch.NotificationDispatchPolicy;
import com.vishop.notification.composition.dispatch.NotificationSettingDispatchPolicy;
import com.vishop.notification.modules.setting.application.api.NotificationAlertRuleApi;
import com.vishop.notification.modules.setting.application.api.NotificationAlertRuleApi.RuleView;
import com.vishop.notification.modules.setting.application.api.NotificationSettingManagementApi;
import com.vishop.notification.modules.setting.application.api.NotificationSettingManagementApi.ChannelView;
import com.vishop.notification.shared.constants.NotificationTypeCode;
import com.vishop.notification.shared.contract.NotificationChannel;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class NotificationSettingDispatchPolicyTest {
  final UUID tenant = UUID.randomUUID();
  final NotificationSettingManagementApi settings = mock(NotificationSettingManagementApi.class);
  final NotificationAlertRuleApi rules = mock(NotificationAlertRuleApi.class);
  final NotificationSettingDispatchPolicy policy =
      new NotificationSettingDispatchPolicy(settings, rules);
  final NotificationTypeCode code = NotificationTypeCode.STOCK_LOW;
  final NotificationDispatchPolicy.TypePolicy type =
      new NotificationDispatchPolicy.TypePolicy(
          code.code(), code.feature().code(), true, code.defaultEnabled(), code.defaultChannels());

  ChannelView channel(
      NotificationChannel channel, boolean enabled, boolean available, boolean verified) {
    return new ChannelView(
        UUID.randomUUID(),
        channel.name(),
        channel,
        true,
        enabled,
        available,
        true,
        verified,
        null,
        null,
        null,
        1,
        Map.of());
  }

  @Test
  void sendsOnlyThroughChannelsAllowedByRuleAndReadyConnection() {
    when(rules.ruleForType(tenant, code))
        .thenReturn(
            new RuleView(
                UUID.randomUUID(),
                code.code(),
                "Low stock",
                true,
                Set.of(NotificationChannel.values()),
                1));
    when(settings.channels(tenant))
        .thenReturn(
            List.of(
                channel(NotificationChannel.IN_APP, true, true, true),
                channel(NotificationChannel.EMAIL, true, true, true),
                channel(NotificationChannel.TELEGRAM, true, true, false),
                channel(NotificationChannel.LARK, false, true, true)));
    assertThat(policy.resolve(tenant, type).channels())
        .containsExactlyInAnyOrder(NotificationChannel.IN_APP, NotificationChannel.EMAIL);
    when(rules.ruleForType(tenant, code))
        .thenReturn(
            new RuleView(
                UUID.randomUUID(),
                code.code(),
                "Low stock",
                true,
                Set.of(NotificationChannel.LARK),
                1));
    assertThat(policy.resolve(tenant, type).channels()).isEmpty();
    assertThat(policy.resolve(tenant, type).selectedRecipients()).isEmpty();
  }

  @Test
  void disabledRuleOrUnavailableTypeDoesNotLoadConnectionSettings() {
    when(rules.ruleForType(tenant, code))
        .thenReturn(
            new RuleView(
                UUID.randomUUID(),
                code.code(),
                "Low stock",
                false,
                Set.of(NotificationChannel.IN_APP),
                1));
    assertThat(policy.resolve(tenant, type).channels()).isEmpty();
    verifyNoInteractions(settings);
    clearInvocations(rules);
    var unavailable =
        new NotificationDispatchPolicy.TypePolicy(
            code.code(), code.feature().code(), false, true, Set.of(NotificationChannel.IN_APP));
    assertThat(policy.resolve(tenant, unavailable).channels()).isEmpty();
    verifyNoInteractions(settings, rules);
  }

  @Test
  void larkIsAvailableWhenAnyOfTheConfiguredGroupsIsEnabledAndVerified() {
    when(rules.ruleForType(tenant, code))
        .thenReturn(
            new RuleView(
                UUID.randomUUID(),
                code.code(),
                "Low stock",
                true,
                Set.of(NotificationChannel.LARK),
                1));
    when(settings.channels(tenant))
        .thenReturn(
            List.of(
                channel(NotificationChannel.LARK, false, true, false),
                channel(NotificationChannel.LARK, true, true, true)));
    assertThat(policy.resolve(tenant, type).channels()).containsExactly(NotificationChannel.LARK);
  }
}
