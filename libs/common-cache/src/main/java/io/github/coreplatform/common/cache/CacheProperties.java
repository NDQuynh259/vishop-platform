package io.github.coreplatform.common.cache;

import java.util.Objects;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Prefix chung của ứng dụng/môi trường; namespace nghiệp vụ do caller quyết định. */
@ConfigurationProperties("vishop.cache")
public record CacheProperties(@DefaultValue("") String keyPrefix) {
  public CacheProperties {
    Objects.requireNonNull(keyPrefix, "keyPrefix");
  }
}
