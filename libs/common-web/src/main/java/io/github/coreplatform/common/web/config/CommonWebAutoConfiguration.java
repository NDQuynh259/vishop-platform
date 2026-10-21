package io.github.coreplatform.common.web.config;

import io.github.coreplatform.common.domain.cqrs.CommandBus;
import io.github.coreplatform.common.domain.cqrs.QueryBus;
import io.github.coreplatform.common.web.DatabaseExceptionHandler;
import io.github.coreplatform.common.web.GlobalExceptionHandler;
import io.github.coreplatform.common.web.RequestIdFilter;
import io.github.coreplatform.common.web.SecurityExceptionHandler;
import io.github.coreplatform.common.web.cqrs.SpringCommandBus;
import io.github.coreplatform.common.web.cqrs.SpringQueryBus;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * Spring Boot AutoConfiguration for ViShop Common Web module.
 * Provides pre-configured GlobalExceptionHandler, RequestIdFilter, CQRS buses,
 * and optional security/database exception handlers.
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class CommonWebAutoConfiguration {

    // #region globalExceptionHandler
    @Bean
    @ConditionalOnMissingBean
    public GlobalExceptionHandler globalExceptionHandler() {
        return new GlobalExceptionHandler();
    }
    // #endregion

    // #region requestIdFilter
    @Bean
    @ConditionalOnMissingBean
    public RequestIdFilter requestIdFilter() {
        return new RequestIdFilter();
    }
    // #endregion

    // #region requestIdFilterRegistration
    @Bean
    @ConditionalOnMissingBean(name = "requestIdFilterRegistration")
    public FilterRegistrationBean<RequestIdFilter> requestIdFilterRegistration(RequestIdFilter filter) {
        FilterRegistrationBean<RequestIdFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }
    // #endregion

    // #region commandBus
    @Bean
    @ConditionalOnMissingBean(CommandBus.class)
    public SpringCommandBus commandBus(ApplicationContext applicationContext) {
        return new SpringCommandBus(applicationContext);
    }
    // #endregion

    // #region queryBus
    @Bean
    @ConditionalOnMissingBean(QueryBus.class)
    public SpringQueryBus queryBus(ApplicationContext applicationContext) {
        return new SpringQueryBus(applicationContext);
    }
    // #endregion

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "org.springframework.security.access.AccessDeniedException")
    public static class SecurityExceptionConfiguration {

        // #region securityExceptionHandler
        @Bean
        @ConditionalOnMissingBean
        public SecurityExceptionHandler securityExceptionHandler() {
            return new SecurityExceptionHandler();
        }
        // #endregion
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "org.springframework.dao.DataIntegrityViolationException")
    public static class DatabaseExceptionConfiguration {

        // #region databaseExceptionHandler
        @Bean
        @ConditionalOnMissingBean
        public DatabaseExceptionHandler databaseExceptionHandler() {
            return new DatabaseExceptionHandler();
        }
        // #endregion
    }
}
