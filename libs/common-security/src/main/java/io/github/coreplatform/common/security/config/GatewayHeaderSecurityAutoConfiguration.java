package io.github.coreplatform.common.security.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.coreplatform.common.cache.CacheKeyBuilder;
import io.github.coreplatform.common.security.gateway.GatewayRolePermissions;
import io.github.coreplatform.common.security.gateway.RedisGatewayRolePermissions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.*;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;

@AutoConfiguration(
    afterName = {
      "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration",
      "io.github.coreplatform.common.cache.CacheAutoConfiguration"
    })
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnBean({StringRedisTemplate.class, CacheKeyBuilder.class})
public class GatewayHeaderSecurityAutoConfiguration {
  @Bean
  @ConditionalOnMissingBean(GatewayRolePermissions.class)
  GatewayRolePermissions gatewayRolePermissions(
      StringRedisTemplate redis,
      ObjectMapper json,
      CacheKeyBuilder keys,
      @Value("${vishop.security.authorization.max-age-seconds:30}") long maxAge) {
    return new RedisGatewayRolePermissions(redis, json, keys, maxAge);
  }
}
