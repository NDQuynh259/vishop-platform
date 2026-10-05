package com.vishop.common.persistence;

import com.vishop.common.events.KafkaHeaders;
import com.vishop.common.events.KafkaTopicNaming;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Publishes committed outbox records to Kafka asynchronously and reliably.
 * Runs on a configurable fixed delay.
 */
@Component
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

    private final OutboxEventRepository repository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    @Value("${vishop.outbox.batch-size:50}")
    private int batchSize;

    @Value("${vishop.outbox.max-retries:5}")
    private int maxRetries;

    public OutboxPublisher(OutboxEventRepository repository,
                           KafkaTemplate<String, String> kafkaTemplate) {
        this.repository = repository;
        this.kafkaTemplate = kafkaTemplate;
    }

    @Scheduled(fixedDelayString = "${vishop.outbox.publish-delay-ms:1000}")
    @Transactional
    public void publishPending() {
        List<OutboxEventEntity> pending = repository.findPendingEvents(PageRequest.of(0, batchSize));
        if (pending.isEmpty()) {
            return;
        }

        for (OutboxEventEntity event : pending) {
            String topic = KafkaTopicNaming.topicFor(event.getAggregateType());
            String partitionKey = event.getAggregateId() != null
                    ? event.getAggregateId().toString()
                    : event.getId().toString();

            try {
                ProducerRecord<String, String> record = new ProducerRecord<>(topic, partitionKey, event.getPayload());

                if (event.getTenantId() != null) {
                    record.headers().add(new RecordHeader(KafkaHeaders.TENANT_ID,
                            event.getTenantId().toString().getBytes(StandardCharsets.UTF_8)));
                }
                if (event.getEventType() != null) {
                    record.headers().add(new RecordHeader(KafkaHeaders.EVENT_TYPE,
                            event.getEventType().getBytes(StandardCharsets.UTF_8)));
                }

                kafkaTemplate.send(record).get();
                event.markPublished(Instant.now());
                repository.save(event);
                log.debug("Successfully published outbox event [{}] to topic [{}]", event.getId(), topic);
            } catch (Exception ex) {
                log.error("Failed to publish outbox event [{}] to topic [{}]: {}",
                        event.getId(), topic, ex.getMessage(), ex);
                event.recordFailure(ex.getMessage(), maxRetries);
                repository.save(event);
            }
        }
    }
}
