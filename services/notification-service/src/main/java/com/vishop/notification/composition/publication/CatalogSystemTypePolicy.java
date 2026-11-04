package com.vishop.notification.composition.publication;

import com.vishop.notification.modules.announcement.application.port.SystemTypePolicy;
import com.vishop.notification.modules.catalog.application.api.CatalogApi;
import java.util.Objects;

public final class CatalogSystemTypePolicy implements SystemTypePolicy {
  private final CatalogApi catalog;

  public CatalogSystemTypePolicy(CatalogApi catalog) {
    this.catalog = Objects.requireNonNull(catalog);
  }

  @Override
  public void requirePublishableSystemType(String code) {
    var type =
        catalog
            .findType(code)
            .orElseThrow(() -> new IllegalArgumentException("Unknown notification type"));
    if (!type.available() || !"SYSTEM".equals(type.category()))
      throw new IllegalArgumentException("Active SYSTEM type required");
  }
}
