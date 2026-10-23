package com.vishop.iam.shared.infrastructure.messaging;

import io.github.coreplatform.common.events.KafkaHeaders;
import io.github.coreplatform.common.events.KafkaTopicNaming;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnProperty(name = "vishop.outbox.enabled", havingValue = "true", matchIfMissing = true)
public class IamOutboxRelay {
  private final JdbcTemplate db;
  private final KafkaTemplate<String, String> kafka;

  public IamOutboxRelay(JdbcTemplate db, KafkaTemplate<String, String> kafka) {
    this.db = db;
    this.kafka = kafka;
  }

  @Scheduled(fixedDelayString = "${vishop.outbox.publish-delay-ms:1000}")
  @Transactional
  public void publish() {
    // Row locks prevent two IAM instances from sending the same batch concurrently.
    var rows =
        db.queryForList(
            "SELECT * FROM outbox_events WHERE status='PENDING' "
                + "ORDER BY occurred_at,id LIMIT 50 FOR UPDATE SKIP LOCKED");
    for (var row : rows) {
      var record =
          new ProducerRecord<String, String>(
              KafkaTopicNaming.topicFor((String) row.get("aggregate_type")),
              row.get("aggregate_id").toString(),
              row.get("payload").toString());
      if (row.get("tenant_id") != null)
        record
            .headers()
            .add(
                KafkaHeaders.TENANT_ID,
                row.get("tenant_id").toString().getBytes(StandardCharsets.UTF_8));
      record
          .headers()
          .add(
              KafkaHeaders.EVENT_TYPE,
              row.get("event_type").toString().getBytes(StandardCharsets.UTF_8));
      try {
        kafka.send(record).get(10, TimeUnit.SECONDS);
        db.update(
            "UPDATE outbox_events SET status='PUBLISHED',published_at=now() WHERE id=?",
            row.get("id"));
      } catch (Exception failure) {
        if (failure instanceof InterruptedException) Thread.currentThread().interrupt();
        db.update(
            "UPDATE outbox_events SET retry_count=retry_count+1,error_message='Kafka delivery failed',"
                + "status=CASE WHEN retry_count>=4 THEN 'FAILED' ELSE 'PENDING' END WHERE id=?",
            row.get("id"));
      }
    }
  }
}
