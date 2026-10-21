package io.github.coreplatform.common.observability.config;

import io.github.coreplatform.common.observability.CorrelationMdcFilter;
import io.micrometer.tracing.Tracer;
import jakarta.servlet.Filter;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
public class ObservabilityAutoConfiguration {

    @Bean
    @ConditionalOnClass(Filter.class)
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    public CorrelationMdcFilter correlationMdcFilter(ObjectProvider<Tracer> tracerProvider) {
        return new CorrelationMdcFilter(tracerProvider);
    }
}
