package io.github.coreplatform.common.events.consumer;

import io.github.coreplatform.common.events.KafkaHeaders;
import io.github.coreplatform.common.security.context.TenantContext;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.springframework.kafka.listener.RecordInterceptor;

/**
 * Kafka record interceptor that automatically extracts X-Tenant-Id from message headers, sets the
 * TenantContext before the listener executes, and clears it after completion.
 */
public class TenantRecordInterceptor<K, V> implements RecordInterceptor<K, V> {

  // #region intercept
  @Override
  public ConsumerRecord<K, V> intercept(ConsumerRecord<K, V> record, Consumer<K, V> consumer) {
    Header tenantHeader = record.headers().lastHeader(KafkaHeaders.TENANT_ID);
    if (tenantHeader != null && tenantHeader.value() != null) {
      try {
        String tenantStr = new String(tenantHeader.value(), StandardCharsets.UTF_8);
        TenantContext.set(UUID.fromString(tenantStr));
      } catch (Exception ignored) {
        TenantContext.clear();
      }
    } else {
      TenantContext.clear();
    }
    return record;
  }

  // #endregion

  // #region afterRecord
  @Override
  public void afterRecord(ConsumerRecord<K, V> record, Consumer<K, V> consumer) {
    TenantContext.clear();
  }
  // #endregion
}
