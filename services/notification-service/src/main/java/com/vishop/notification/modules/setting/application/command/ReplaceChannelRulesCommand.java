package com.vishop.notification.modules.setting.application.command;

import com.vishop.notification.modules.setting.application.api.NotificationSettingManagementApi.ChannelRulesView;
import io.github.coreplatform.common.domain.cqrs.Command;
import java.util.Set;
import java.util.UUID;

public record ReplaceChannelRulesCommand(
    UUID tenantId, UUID settingId, long version, Set<UUID> ruleIds)
    implements Command<ChannelRulesView> {}
