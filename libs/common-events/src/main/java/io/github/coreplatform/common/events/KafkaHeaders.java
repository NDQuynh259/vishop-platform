package io.github.coreplatform.common.events;

/** Standard Kafka header names used across ViShop microservices. */
public final class KafkaHeaders {

  public static final String TENANT_ID = "X-Tenant-Id";
  public static final String CORRELATION_ID = "X-Correlation-Id";
  public static final String CAUSATION_ID = "X-Causation-Id";
  public static final String EVENT_TYPE = "X-Event-Type";
  public static final String PRODUCER = "X-Producer";
  public static final String SCHEMA_VERSION = "X-Schema-Version";

  // #region KafkaHeaders
  private KafkaHeaders() {}
  // #endregion
}
