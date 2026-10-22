package io.github.coreplatform.common.web.cqrs;

import io.github.coreplatform.common.domain.cqrs.CommandBus;
import io.github.coreplatform.common.domain.cqrs.QueryBus;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;

/**
 * Auto-configuration for CQRS buses (CommandBus, QueryBus).
 */
@AutoConfiguration
public class CqrsAutoConfiguration {

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
}
