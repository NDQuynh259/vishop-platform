package io.github.coreplatform.common.web.cqrs;

import io.github.coreplatform.common.domain.cqrs.Query;
import io.github.coreplatform.common.domain.cqrs.QueryBus;
import io.github.coreplatform.common.domain.cqrs.QueryHandler;
import jakarta.annotation.PostConstruct;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.ApplicationContext;
import org.springframework.core.ResolvableType;
import org.springframework.stereotype.Component;

/**
 * Spring-managed QueryBus implementation that automatically discovers
 * and routes queries to their corresponding {@link QueryHandler}.
 */
@Component
public class SpringQueryBus implements QueryBus {

    private final ApplicationContext applicationContext;
    private final Map<Class<?>, QueryHandler<?, ?>> handlerRegistry = new ConcurrentHashMap<>();

    // #region SpringQueryBus
    public SpringQueryBus(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }
    // #endregion

    // #region registerHandlers
    @PostConstruct
    public void registerHandlers() {
        Map<String, QueryHandler> beans = applicationContext.getBeansOfType(QueryHandler.class);
        for (QueryHandler<?, ?> handler : beans.values()) {
            Class<?> targetClass = AopUtils.getTargetClass(handler);
            ResolvableType resolvableType = ResolvableType.forClass(targetClass).as(QueryHandler.class);
            Class<?> queryType = resolvableType.getGeneric(0).resolve();
            if (queryType != null) {
                handlerRegistry.put(queryType, handler);
            }
        }
    }
    // #endregion

    // #region dispatch
    @Override
    @SuppressWarnings("unchecked")
    public <R> R dispatch(Query<R> query) {
        if (query == null) {
            throw new IllegalArgumentException("Query cannot be null");
        }
        QueryHandler<Query<R>, R> handler = (QueryHandler<Query<R>, R>) handlerRegistry.get(query.getClass());
        if (handler == null) {
            // Lazy lookup in case handlers were registered dynamically
            handler = (QueryHandler<Query<R>, R>) findHandler(query.getClass());
        }
        if (handler == null) {
            throw new IllegalStateException("No QueryHandler registered for query: " + query.getClass().getName());
        }
        return handler.handle(query);
    }
    // #endregion

    // #region findHandler
    private QueryHandler<?, ?> findHandler(Class<?> queryClass) {
        Map<String, QueryHandler> beans = applicationContext.getBeansOfType(QueryHandler.class);
        for (QueryHandler<?, ?> handler : beans.values()) {
            Class<?> targetClass = AopUtils.getTargetClass(handler);
            ResolvableType resolvableType = ResolvableType.forClass(targetClass).as(QueryHandler.class);
            Class<?> targetQuery = resolvableType.getGeneric(0).resolve();
            if (queryClass.equals(targetQuery)) {
                handlerRegistry.put(queryClass, handler);
                return handler;
            }
        }
        return null;
    }
    // #endregion
}
