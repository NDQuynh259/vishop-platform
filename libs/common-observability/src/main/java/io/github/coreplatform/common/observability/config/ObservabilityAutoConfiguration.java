package io.github.coreplatform.common.observability.config;

import io.github.coreplatform.common.observability.CorrelationMdcFilter;
import io.micrometer.tracing.Tracer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@AutoConfiguration
public class ObservabilityAutoConfiguration {

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "jakarta.servlet.Filter")
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    static class ServletFilterConfiguration {

        // #region correlationMdcFilter
        @Bean
        public CorrelationMdcFilter correlationMdcFilter(ObjectProvider<Tracer> tracerProvider) {
            return new CorrelationMdcFilter(tracerProvider);
        }
        // #endregion
    }
}
