package com.vishop.gateway.filter;

import java.util.UUID;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Validates the syntax of X-Tenant-Id header when supplied by the client.
 */
@Component
public class TenantResolutionGlobalFilter implements GlobalFilter, Ordered {

    public static final String TENANT_ID_HEADER = "X-Tenant-Id";

    // #region filter
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String tenantHeader = exchange.getRequest().getHeaders().getFirst(TENANT_ID_HEADER);

        if (tenantHeader != null && !tenantHeader.isBlank()) {
            try {
                UUID.fromString(tenantHeader.trim());
            } catch (IllegalArgumentException e) {
                ServerHttpResponse response = exchange.getResponse();
                response.setStatusCode(HttpStatus.BAD_REQUEST);
                response.getHeaders().setContentType(MediaType.APPLICATION_PROBLEM_JSON);

                String errorJson = """
                        {
                          "type": "/errors/invalid-tenant-id",
                          "title": "Invalid Tenant ID",
                          "status": 400,
                          "detail": "The provided X-Tenant-Id header is not a valid UUID format."
                        }
                        """;

                DataBuffer buffer = response.bufferFactory().wrap(errorJson.getBytes());
                return response.writeWith(Mono.just(buffer));
            }
        }

        return chain.filter(exchange);
    }
    // #endregion

    // #region getOrder
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 1;
    }
    // #endregion
}
