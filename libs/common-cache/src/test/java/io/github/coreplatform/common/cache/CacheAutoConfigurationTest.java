package io.github.coreplatform.common.cache;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.data.redis.core.StringRedisTemplate;

class CacheAutoConfigurationTest {
  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withInitializer(
              context ->
                  context
                      .getEnvironment()
                      .getPropertySources()
                      .remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME))
          .withConfiguration(AutoConfigurations.of(CacheAutoConfiguration.class));

  @Test
  void libraryDefaultsToNoApplicationPrefixAndDoesNotCreateRedisConnections() {
    runner.run(
        context -> {
          assertThat(context)
              .hasSingleBean(CacheKeyBuilder.class)
              .hasSingleBean(CacheProperties.class);
          assertThat(context.getBean(CacheProperties.class).keyPrefix()).isEmpty();
          assertThat(context.getBean(CacheKeyBuilder.class).build("catalog", "product", 42))
              .isEqualTo("catalog:product:42");
          assertThat(context)
              .doesNotHaveBean(StringRedisTemplate.class)
              .doesNotHaveBean(CacheService.class);
        });
  }

  @Test
  void bindsSharedPrefixAndLetsApplicationsOverrideTheBuilder() {
    runner
        .withPropertyValues("vishop.cache.key-prefix=vishop:prod:")
        .run(
            context ->
                assertThat(context.getBean(CacheKeyBuilder.class).build("catalog", "product", 42))
                    .isEqualTo("vishop:prod:catalog:product:42"));
    var override = new CacheKeyBuilder("custom:");
    runner
        .withBean(CacheKeyBuilder.class, () -> override)
        .run(context -> assertThat(context.getBean(CacheKeyBuilder.class)).isSameAs(override));
  }

  @Test
  void applicationPlaceholderKeepsLegacyAuthEnvironmentAndPrefersTheSharedVariable() {
    var application =
        runner.withPropertyValues(
            "vishop.cache.key-prefix=${CACHE_KEY_PREFIX:${AUTH_CACHE_PREFIX:vishop:}}");
    application.run(
        context ->
            assertThat(context.getBean(CacheKeyBuilder.class).build("catalog", 42))
                .isEqualTo("vishop:catalog:42"));
    application
        .withPropertyValues("AUTH_CACHE_PREFIX=legacy:")
        .run(
            context ->
                assertThat(context.getBean(CacheKeyBuilder.class).build("catalog", 42))
                    .isEqualTo("legacy:catalog:42"));
    application
        .withPropertyValues("AUTH_CACHE_PREFIX=legacy:", "CACHE_KEY_PREFIX=shared:")
        .run(
            context ->
                assertThat(context.getBean(CacheKeyBuilder.class).build("catalog", 42))
                    .isEqualTo("shared:catalog:42"));
  }
}
