package io.github.coreplatform.common.observability;

import static io.github.coreplatform.common.observability.TracingConstants.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import jakarta.servlet.ServletException;
import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.slf4j.MDC;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class CorrelationMdcFilterTest {

  // #region shouldExtractExistingHeadersAndSetResponse
  @Test
  @DisplayName(
      "Should extract existing headers, populate MDC during execution, and set response headers")
  void shouldExtractExistingHeadersAndSetResponse() throws ServletException, IOException {
    @SuppressWarnings("unchecked")
    ObjectProvider<Tracer> tracerProvider = Mockito.mock(ObjectProvider.class);
    when(tracerProvider.getIfAvailable()).thenReturn(null);

    CorrelationMdcFilter filter = new CorrelationMdcFilter(tracerProvider);

    MockHttpServletRequest request = new MockHttpServletRequest();
    String expectedCorrelationId = UUID.randomUUID().toString();
    String expectedRequestId = UUID.randomUUID().toString();
    String expectedTenantId = UUID.randomUUID().toString();
    String expectedUserId = "user-123";

    request.addHeader(CORRELATION_ID_HEADER, expectedCorrelationId);
    request.addHeader(REQUEST_ID_HEADER, expectedRequestId);
    request.addHeader(TENANT_ID_HEADER, expectedTenantId);
    request.addHeader(USER_ID_HEADER, expectedUserId);

    MockHttpServletResponse response = new MockHttpServletResponse();

    AtomicReference<String> mdcCorr = new AtomicReference<>();
    AtomicReference<String> mdcReq = new AtomicReference<>();
    AtomicReference<String> mdcTenant = new AtomicReference<>();
    AtomicReference<String> mdcUser = new AtomicReference<>();

    MockFilterChain filterChain =
        new MockFilterChain() {
          @Override
          public void doFilter(
              jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res) {
            mdcCorr.set(MDC.get(MDC_CORRELATION_ID));
            mdcReq.set(MDC.get(MDC_REQUEST_ID));
            mdcTenant.set(MDC.get(MDC_TENANT_ID));
            mdcUser.set(MDC.get(MDC_USER_ID));
          }
        };

    filter.doFilter(request, response, filterChain);

    // Verify inside filter execution
    assertEquals(expectedCorrelationId, mdcCorr.get());
    assertEquals(expectedRequestId, mdcReq.get());
    assertEquals(expectedTenantId, mdcTenant.get());
    assertEquals(expectedUserId, mdcUser.get());

    // Verify response headers
    assertEquals(expectedCorrelationId, response.getHeader(CORRELATION_ID_HEADER));
    assertEquals(expectedRequestId, response.getHeader(REQUEST_ID_HEADER));
    assertEquals(expectedTenantId, response.getHeader(TENANT_ID_HEADER));

    // Verify MDC is cleared after request
    assertNull(MDC.get(MDC_CORRELATION_ID));
    assertNull(MDC.get(MDC_REQUEST_ID));
    assertNull(MDC.get(MDC_TENANT_ID));
    assertNull(MDC.get(MDC_USER_ID));
  }

  // #endregion

  // #region shouldGenerateFallbackIdsWhenMissing
  @Test
  @DisplayName("Should generate fallback UUIDs when correlation and request IDs are missing")
  void shouldGenerateFallbackIdsWhenMissing() throws ServletException, IOException {
    @SuppressWarnings("unchecked")
    ObjectProvider<Tracer> tracerProvider = Mockito.mock(ObjectProvider.class);
    when(tracerProvider.getIfAvailable()).thenReturn(null);

    CorrelationMdcFilter filter = new CorrelationMdcFilter(tracerProvider);

    MockHttpServletRequest request = new MockHttpServletRequest();
    MockHttpServletResponse response = new MockHttpServletResponse();

    AtomicReference<String> mdcCorr = new AtomicReference<>();
    AtomicReference<String> mdcReq = new AtomicReference<>();

    MockFilterChain filterChain =
        new MockFilterChain() {
          @Override
          public void doFilter(
              jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res) {
            mdcCorr.set(MDC.get(MDC_CORRELATION_ID));
            mdcReq.set(MDC.get(MDC_REQUEST_ID));
          }
        };

    filter.doFilter(request, response, filterChain);

    assertNotNull(mdcCorr.get());
    assertNotNull(mdcReq.get());
    assertFalse(mdcCorr.get().isBlank());
    assertFalse(mdcReq.get().isBlank());

    assertEquals(mdcCorr.get(), response.getHeader(CORRELATION_ID_HEADER));
    assertEquals(mdcReq.get(), response.getHeader(REQUEST_ID_HEADER));
  }

  // #endregion

  // #region shouldInjectTraceAndSpanIdWhenTracerPresent
  @Test
  @DisplayName("Should inject traceId and spanId to MDC when Tracer has current span")
  void shouldInjectTraceAndSpanIdWhenTracerPresent() throws ServletException, IOException {
    Tracer mockTracer = Mockito.mock(Tracer.class);
    Span mockSpan = Mockito.mock(Span.class);
    TraceContext mockContext = Mockito.mock(TraceContext.class);

    when(mockContext.traceId()).thenReturn("trace-abc-123");
    when(mockContext.spanId()).thenReturn("span-xyz-789");
    when(mockSpan.context()).thenReturn(mockContext);
    when(mockTracer.currentSpan()).thenReturn(mockSpan);

    @SuppressWarnings("unchecked")
    ObjectProvider<Tracer> tracerProvider = Mockito.mock(ObjectProvider.class);
    when(tracerProvider.getIfAvailable()).thenReturn(mockTracer);

    CorrelationMdcFilter filter = new CorrelationMdcFilter(tracerProvider);

    MockHttpServletRequest request = new MockHttpServletRequest();
    MockHttpServletResponse response = new MockHttpServletResponse();

    AtomicReference<String> mdcTrace = new AtomicReference<>();
    AtomicReference<String> mdcSpan = new AtomicReference<>();

    MockFilterChain filterChain =
        new MockFilterChain() {
          @Override
          public void doFilter(
              jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res) {
            mdcTrace.set(MDC.get(MDC_TRACE_ID));
            mdcSpan.set(MDC.get(MDC_SPAN_ID));
          }
        };

    filter.doFilter(request, response, filterChain);

    assertEquals("trace-abc-123", mdcTrace.get());
    assertEquals("span-xyz-789", mdcSpan.get());
    assertEquals("trace-abc-123", response.getHeader(TRACE_ID_HEADER));
  }

  // #endregion

  // #region shouldLogWithFullMdcContextDuringExecution
  @Test
  @DisplayName("Should demonstrate full MDC lifecycle and logging integration")
  void shouldLogWithFullMdcContextDuringExecution() throws ServletException, IOException {
    org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(CorrelationMdcFilterTest.class);

    Tracer mockTracer = Mockito.mock(Tracer.class);
    Span mockSpan = Mockito.mock(Span.class);
    TraceContext mockContext = Mockito.mock(TraceContext.class);

    when(mockContext.traceId()).thenReturn("0af7651916cd43dd8448eb211c80319c");
    when(mockContext.spanId()).thenReturn("b7ad6b7169203331");
    when(mockSpan.context()).thenReturn(mockContext);
    when(mockTracer.currentSpan()).thenReturn(mockSpan);

    @SuppressWarnings("unchecked")
    ObjectProvider<Tracer> tracerProvider = Mockito.mock(ObjectProvider.class);
    when(tracerProvider.getIfAvailable()).thenReturn(mockTracer);

    CorrelationMdcFilter filter = new CorrelationMdcFilter(tracerProvider);

    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/tenants");
    request.addHeader(TENANT_ID_HEADER, "0192e2b3-8c4d-7000-8000-000000000001");
    request.addHeader(USER_ID_HEADER, "usr_9999");
    MockHttpServletResponse response = new MockHttpServletResponse();

    AtomicReference<java.util.Map<String, String>> capturedMdc = new AtomicReference<>();

    MockFilterChain filterChain =
        new MockFilterChain() {
          @Override
          public void doFilter(
              jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res) {
            // Capture all MDC keys
            capturedMdc.set(MDC.getCopyOfContextMap());
            // Emit sample log to demonstrate MDC presence in log output
            log.info(
                "Processing request inside controller with MDC context: {}",
                MDC.getCopyOfContextMap());
          }
        };

    filter.doFilter(request, response, filterChain);

    java.util.Map<String, String> mdc = capturedMdc.get();
    assertNotNull(mdc);
    assertEquals("0af7651916cd43dd8448eb211c80319c", mdc.get(MDC_TRACE_ID));
    assertEquals("b7ad6b7169203331", mdc.get(MDC_SPAN_ID));
    assertEquals("0192e2b3-8c4d-7000-8000-000000000001", mdc.get(MDC_TENANT_ID));
    assertEquals("usr_9999", mdc.get(MDC_USER_ID));
    assertNotNull(mdc.get(MDC_CORRELATION_ID));
    assertNotNull(mdc.get(MDC_REQUEST_ID));

    // Response headers check
    assertEquals("0af7651916cd43dd8448eb211c80319c", response.getHeader(TRACE_ID_HEADER));
    assertEquals(mdc.get(MDC_CORRELATION_ID), response.getHeader(CORRELATION_ID_HEADER));
    assertEquals("0192e2b3-8c4d-7000-8000-000000000001", response.getHeader(TENANT_ID_HEADER));

    // MDC cleaned up
    assertNull(MDC.getCopyOfContextMap());
  }
  // #endregion
}
