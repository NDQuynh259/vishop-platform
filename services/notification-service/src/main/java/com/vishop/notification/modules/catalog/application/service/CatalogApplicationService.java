package com.vishop.notification.modules.catalog.application.service;

import com.vishop.notification.modules.catalog.application.api.CatalogApi;
import com.vishop.notification.modules.catalog.domain.port.NotificationCatalogRepository;
import java.util.Objects;
import java.util.Optional;

public final class CatalogApplicationService implements CatalogApi {
  private final NotificationCatalogRepository repository;

  public CatalogApplicationService(NotificationCatalogRepository repository) {
    this.repository = Objects.requireNonNull(repository);
  }

  @Override
  public Optional<Type> findType(String code) {
    return repository
        .findType(code)
        .map(
            type ->
                new Type(
                    type.code(),
                    type.featureCode(),
                    type.category().name(),
                    type.active() && type.featureActive(),
                    type.defaultEnabled(),
                    type.defaultChannels()));
  }
}
