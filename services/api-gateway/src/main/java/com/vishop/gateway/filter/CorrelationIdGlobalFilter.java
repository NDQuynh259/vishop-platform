package com.vishop.gateway.filter;

import io.github.coreplatform.common.domain.UuidV7;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Ensures every incoming request has valid X-Correlation-Id and X-Request-Id headers.
 * Propagates them downstream to microservices and reflects them in the client response.
 */
@Component
public class CorrelationIdGlobalFilter implements GlobalFilter, Ordered {

    public static final String CORRELATION_ID_HEADER = "X-Correlation-Id";
    public static final String REQUEST_ID_HEADER = "X-Request-Id";

    // #region filter
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();

        String correlationId = request.getHeaders().getFirst(CORRELATION_ID_HEADER);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UuidV7.nextId().toString();
        }

        String requestId = request.getHeaders().getFirst(REQUEST_ID_HEADER);
        if (requestId == null || requestId.isBlank()) {
            requestId = UuidV7.nextId().toString();
        }

        final String finalCorrelationId = correlationId;
        final String finalRequestId = requestId;

        // Mutate downstream request headers
        ServerHttpRequest mutatedRequest = request.mutate()
                .header(CORRELATION_ID_HEADER, finalCorrelationId)
                .header(REQUEST_ID_HEADER, finalRequestId)
                .build();

        // Mutate client response headers
        exchange.getResponse().getHeaders().set(CORRELATION_ID_HEADER, finalCorrelationId);
        exchange.getResponse().getHeaders().set(REQUEST_ID_HEADER, finalRequestId);

        return chain.filter(exchange.mutate().request(mutatedRequest).build());
    }
    // #endregion

    // #region getOrder
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
    // #endregion
}
