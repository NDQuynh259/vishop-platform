package com.vishop.common.events.publisher;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vishop.common.events.EventEnvelope;
import com.vishop.common.events.KafkaHeaders;
import com.vishop.common.events.KafkaTopicNaming;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

/**
 * High-level Kafka event publisher.
 * Automatically attaches partition keys (aggregateId) to guarantee ordering per aggregate,
 * populates standard tracing and tenancy headers, and serializes EventEnvelope to JSON.
 */
@Component
public class KafkaEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(KafkaEventPublisher.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public KafkaEventPublisher(KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * Publishes an event envelope to a topic automatically derived from its aggregate type.
     */
    public CompletableFuture<SendResult<String, String>> publish(EventEnvelope envelope) {
        String topic = KafkaTopicNaming.topicFor(envelope.aggregateType());
        return publish(topic, envelope);
    }

    /**
     * Publishes an event envelope to an explicit topic.
     */
    public CompletableFuture<SendResult<String, String>> publish(String topic, EventEnvelope envelope) {
        try {
            String payloadJson = objectMapper.writeValueAsString(envelope);
            String partitionKey = envelope.aggregateId() != null
                    ? envelope.aggregateId().toString()
                    : envelope.eventId().toString();

            ProducerRecord<String, String> record = new ProducerRecord<>(topic, partitionKey, payloadJson);

            // Populate tracing & tenancy headers
            if (envelope.tenantId() != null) {
                record.headers().add(new RecordHeader(KafkaHeaders.TENANT_ID,
                        envelope.tenantId().toString().getBytes(StandardCharsets.UTF_8)));
            }
            if (envelope.correlationId() != null) {
                record.headers().add(new RecordHeader(KafkaHeaders.CORRELATION_ID,
                        envelope.correlationId().toString().getBytes(StandardCharsets.UTF_8)));
            }
            if (envelope.causationId() != null) {
                record.headers().add(new RecordHeader(KafkaHeaders.CAUSATION_ID,
                        envelope.causationId().toString().getBytes(StandardCharsets.UTF_8)));
            }
            if (envelope.eventType() != null) {
                record.headers().add(new RecordHeader(KafkaHeaders.EVENT_TYPE,
                        envelope.eventType().getBytes(StandardCharsets.UTF_8)));
            }
            if (envelope.producer() != null) {
                record.headers().add(new RecordHeader(KafkaHeaders.PRODUCER,
                        envelope.producer().getBytes(StandardCharsets.UTF_8)));
            }

            return kafkaTemplate.send(record).whenComplete((result, ex) -> {
                if (ex != null) {
                    log.error("Failed to publish event [{}] type [{}] to topic [{}] key [{}]",
                            envelope.eventId(), envelope.eventType(), topic, partitionKey, ex);
                } else {
                    log.debug("Published event [{}] type [{}] to topic [{}] partition [{}] offset [{}]",
                            envelope.eventId(), envelope.eventType(), topic,
                            result.getRecordMetadata().partition(),
                            result.getRecordMetadata().offset());
                }
            });
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize EventEnvelope [{}]", envelope.eventId(), e);
            CompletableFuture<SendResult<String, String>> future = new CompletableFuture<>();
            future.completeExceptionally(e);
            return future;
        }
    }
}
