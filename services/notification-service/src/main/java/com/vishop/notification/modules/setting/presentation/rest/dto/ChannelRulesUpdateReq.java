package com.vishop.notification.modules.setting.presentation.rest.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.util.Set;
import java.util.UUID;

public record ChannelRulesUpdateReq(
    @NotNull @PositiveOrZero Long version, @NotNull Set<@NotNull UUID> ruleIds) {}
