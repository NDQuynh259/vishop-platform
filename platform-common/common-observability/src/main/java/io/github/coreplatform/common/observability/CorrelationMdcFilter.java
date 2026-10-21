package io.github.coreplatform.common.observability;

import io.github.coreplatform.common.domain.UuidV7;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.MDC;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.Ordered;
import org.springframework.web.filter.OncePerRequestFilter;

import static io.github.coreplatform.common.observability.TracingConstants.*;

/**
 * Filter to extract correlation, request, and tenant IDs from HTTP headers,
 * inject them into SLF4J MDC, synchronize with Micrometer Tracing traceId/spanId,
 * and reflect them back to client response headers.
 */
public class CorrelationMdcFilter extends OncePerRequestFilter implements Ordered {

    private final ObjectProvider<Tracer> tracerProvider;

    public CorrelationMdcFilter(ObjectProvider<Tracer> tracerProvider) {
        this.tracerProvider = tracerProvider;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        try {
            // 1. Correlation ID
            String correlationId = request.getHeader(CORRELATION_ID_HEADER);
            if (correlationId == null || correlationId.isBlank()) {
                correlationId = UuidV7.nextId().toString();
            }
            MDC.put(MDC_CORRELATION_ID, correlationId);
            response.setHeader(CORRELATION_ID_HEADER, correlationId);

            // 2. Request ID
            String requestId = request.getHeader(REQUEST_ID_HEADER);
            if (requestId == null || requestId.isBlank()) {
                requestId = UuidV7.nextId().toString();
            }
            MDC.put(MDC_REQUEST_ID, requestId);
            response.setHeader(REQUEST_ID_HEADER, requestId);

            // 3. Tenant ID (if present)
            String tenantId = request.getHeader(TENANT_ID_HEADER);
            if (tenantId != null && !tenantId.isBlank()) {
                MDC.put(MDC_TENANT_ID, tenantId);
                response.setHeader(TENANT_ID_HEADER, tenantId);
            }

            // 4. User ID (if present)
            String userId = request.getHeader(USER_ID_HEADER);
            if (userId != null && !userId.isBlank()) {
                MDC.put(MDC_USER_ID, userId);
            }

            // 5. OpenTelemetry / Micrometer TraceId and SpanId
            Tracer tracer = tracerProvider.getIfAvailable();
            if (tracer != null) {
                Span currentSpan = tracer.currentSpan();
                if (currentSpan != null) {
                    MDC.put(MDC_TRACE_ID, currentSpan.context().traceId());
                    MDC.put(MDC_SPAN_ID, currentSpan.context().spanId());
                    response.setHeader(TRACE_ID_HEADER, currentSpan.context().traceId());
                }
            }

            filterChain.doFilter(request, response);

        } finally {
            MDC.clear();
        }
    }

    @Override
    public int getOrder() {
        // Run immediately after basic security/cors filters, but before application controllers
        return Ordered.HIGHEST_PRECEDENCE + 5;
    }
}
