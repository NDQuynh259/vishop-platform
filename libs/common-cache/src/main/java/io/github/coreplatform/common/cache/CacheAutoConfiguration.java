package io.github.coreplatform.common.cache;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/** Chỉ đăng ký cấu hình/key builder; service tự chọn Redis connection và cache adapter. */
@AutoConfiguration
@EnableConfigurationProperties(CacheProperties.class)
public class CacheAutoConfiguration {
  @Bean
  @ConditionalOnMissingBean
  public CacheKeyBuilder cacheKeyBuilder(CacheProperties properties) {
    return new CacheKeyBuilder(properties.keyPrefix());
  }
}
