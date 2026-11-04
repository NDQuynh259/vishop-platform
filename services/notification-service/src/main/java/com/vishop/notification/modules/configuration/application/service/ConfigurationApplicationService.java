package com.vishop.notification.modules.configuration.application.service;

import com.vishop.notification.modules.configuration.application.api.ConfigurationApi;
import com.vishop.notification.modules.configuration.domain.entity.TenantConfiguration;
import com.vishop.notification.modules.configuration.domain.port.NotificationConfigurationRepository;
import com.vishop.notification.shared.contract.NotificationChannel;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public final class ConfigurationApplicationService implements ConfigurationApi {
  private final NotificationConfigurationRepository repository;

  public ConfigurationApplicationService(NotificationConfigurationRepository repository) {
    this.repository = Objects.requireNonNull(repository);
  }

  @Override
  public RoutingPolicy resolve(UUID tenantId, TypePolicy type) {
    Objects.requireNonNull(tenantId);
    Objects.requireNonNull(type);
    if (!type.available()) return new RoutingPolicy(Set.of(), Set.of());
    var config = repository.loadTenant(tenantId, type.code(), type.featureCode());
    return new RoutingPolicy(
        config.resolve(type.defaultEnabled(), type.defaultChannels()),
        config.typeRule().map(TenantConfiguration.TypeRule::recipientIds).orElse(Set.of()));
  }

  @Override
  public Set<NotificationChannel> restrictForUser(
      UUID tenantId, UUID userId, String type, Set<NotificationChannel> tenantChannels) {
    return repository
        .findPreference(tenantId, userId, type)
        .map(preference -> preference.restrict(tenantChannels))
        .orElseGet(() -> Set.copyOf(tenantChannels));
  }
}
