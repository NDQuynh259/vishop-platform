package com.vishop.notification.modules.setting.presentation.rest;

import io.github.coreplatform.common.domain.exception.ForbiddenException;
import io.github.coreplatform.common.domain.exception.UnauthorizedException;
import io.github.coreplatform.common.security.gateway.GatewayPrincipal;
import java.util.UUID;

/** Uses the authenticated gateway principal only; no tenant/user identity from client input. */
final class NotificationSettingAccess {
  static final String VIEW = "notifications.settings.view";
  static final String MANAGE = "notifications.settings.manage";

  private NotificationSettingAccess() {}

  static UUID tenant(GatewayPrincipal actor, String permission) {
    if (actor == null || actor.userId() == null)
      throw new UnauthorizedException("Authentication required");
    if (actor.tenantId() == null) throw new ForbiddenException("Active tenant membership required");
    if (permission != null && !actor.permissions().contains(permission))
      throw new ForbiddenException("Missing notification settings permission");
    return actor.tenantId();
  }
}
