package com.vishop.iam.infra.messaging;

import io.github.coreplatform.common.events.KafkaHeaders;
import io.github.coreplatform.common.events.KafkaTopicNaming;
import io.github.coreplatform.common.persistence.OutboxEventRepository;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnProperty(name = "vishop.outbox.enabled", havingValue = "true", matchIfMissing = true)
public class IamOutboxRelay {
  private final OutboxEventRepository events;
  private final KafkaTemplate<String, String> kafka;

  public IamOutboxRelay(OutboxEventRepository events, KafkaTemplate<String, String> kafka) {
    this.events = events;
    this.kafka = kafka;
  }

  @Scheduled(fixedDelayString = "${vishop.outbox.publish-delay-ms:1000}")
  @Transactional
  public void publish() {
    // Row locks prevent two IAM instances from sending the same batch concurrently.
    var rows = events.lockPendingEvents();
    for (var row : rows) {
      var record =
          new ProducerRecord<String, String>(
              KafkaTopicNaming.topicFor(row.getAggregateType()),
              row.getAggregateId().toString(),
              row.getPayload());
      if (row.getTenantId() != null)
        record
            .headers()
            .add(
                KafkaHeaders.TENANT_ID,
                row.getTenantId().toString().getBytes(StandardCharsets.UTF_8));
      record
          .headers()
          .add(KafkaHeaders.EVENT_TYPE, row.getEventType().getBytes(StandardCharsets.UTF_8));
      try {
        kafka.send(record).get(10, TimeUnit.SECONDS);
        row.markPublished(Instant.now());
      } catch (Exception failure) {
        if (failure instanceof InterruptedException) Thread.currentThread().interrupt();
        row.recordFailure("Kafka delivery failed", 5);
      }
    }
  }
}
