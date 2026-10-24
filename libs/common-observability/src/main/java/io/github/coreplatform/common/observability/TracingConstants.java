package io.github.coreplatform.common.observability;

public final class TracingConstants {

  // #region TracingConstants
  private TracingConstants() {}

  // #endregion

  // HTTP / Kafka Headers
  public static final String CORRELATION_ID_HEADER = "X-Correlation-Id";
  public static final String REQUEST_ID_HEADER = "X-Request-Id";
  public static final String TENANT_ID_HEADER = "X-Tenant-Id";
  public static final String USER_ID_HEADER = "X-User-Id";
  public static final String TRACE_ID_HEADER = "X-Trace-Id";

  // SLF4J MDC Keys
  public static final String MDC_CORRELATION_ID = "correlationId";
  public static final String MDC_REQUEST_ID = "requestId";
  public static final String MDC_TENANT_ID = "tenantId";
  public static final String MDC_USER_ID = "userId";
  public static final String MDC_TRACE_ID = "traceId";
  public static final String MDC_SPAN_ID = "spanId";
}
