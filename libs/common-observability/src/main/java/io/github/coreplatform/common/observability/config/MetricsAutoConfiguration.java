package io.github.coreplatform.common.observability.config;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.autoconfigure.metrics.MeterRegistryCustomizer;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
public class MetricsAutoConfiguration {

    // #region commonMetricsTags
    @Bean
    public MeterRegistryCustomizer<MeterRegistry> commonMetricsTags(
            @Value("${spring.application.name:unknown-service}") String applicationName,
            @Value("${spring.profiles.active:default}") String environment) {
        return registry -> registry.config()
                .commonTags("application", applicationName)
                .commonTags("env", environment);
    }
    // #endregion
}
