package io.github.coreplatform.common.security.config;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.coreplatform.common.cache.CacheAutoConfiguration;
import io.github.coreplatform.common.security.gateway.GatewayRolePermissions;
import io.github.coreplatform.common.security.gateway.RedisGatewayRolePermissions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;

class GatewayHeaderSecurityAutoConfigurationTest {
  final WebApplicationContextRunner context =
      new WebApplicationContextRunner()
          .withConfiguration(
              AutoConfigurations.of(
                  CacheAutoConfiguration.class,
                  RedisAutoConfiguration.class,
                  GatewayHeaderSecurityAutoConfiguration.class))
          .withBean(ObjectMapper.class, ObjectMapper::new);

  @Test
  void servletServiceGetsSharedRedisReaderWithoutCustomSecurityBeans() {
    context.run(
        application ->
            assertThat(application)
                .hasSingleBean(GatewayRolePermissions.class)
                .hasSingleBean(RedisGatewayRolePermissions.class));
  }

  @Test
  void customPermissionResolverTakesPrecedence() {
    context
        .withBean(
            GatewayRolePermissions.class,
            () -> org.mockito.Mockito.mock(GatewayRolePermissions.class))
        .run(
            application ->
                assertThat(application)
                    .hasSingleBean(GatewayRolePermissions.class)
                    .doesNotHaveBean(RedisGatewayRolePermissions.class));
  }
}
