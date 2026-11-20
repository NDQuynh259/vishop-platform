package com.vishop.notification.modules.setting.application.query;

import com.vishop.notification.modules.setting.application.api.NotificationSettingManagementApi.ChannelRulesView;
import io.github.coreplatform.common.domain.cqrs.Query;
import java.util.UUID;

public record GetChannelRulesQuery(UUID tenantId, UUID settingId)
    implements Query<ChannelRulesView> {}
