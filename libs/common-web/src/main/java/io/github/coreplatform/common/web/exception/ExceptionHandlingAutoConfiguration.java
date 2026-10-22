package io.github.coreplatform.common.web.exception;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Auto-configuration for Web Exception Handlers:
 * - GlobalExceptionHandler (always active in Servlet web apps)
 * - SecurityExceptionHandler (conditional on Spring Security)
 * - DatabaseExceptionHandler (conditional on Spring DAO / DataIntegrityViolationException)
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class ExceptionHandlingAutoConfiguration {

    // #region globalExceptionHandler
    @Bean
    @ConditionalOnMissingBean
    public GlobalExceptionHandler globalExceptionHandler() {
        return new GlobalExceptionHandler();
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
