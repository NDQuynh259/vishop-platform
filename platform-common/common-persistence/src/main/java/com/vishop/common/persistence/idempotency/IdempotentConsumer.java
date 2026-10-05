package com.vishop.common.persistence.idempotency;

import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service to execute Kafka message processing idempotently.
 * Guarantees that at-least-once message delivery does not lead to duplicate side effects.
 */
@Service
public class IdempotentConsumer {

    private static final Logger log = LoggerFactory.getLogger(IdempotentConsumer.class);

    private final ConsumedMessageRepository repository;

    public IdempotentConsumer(ConsumedMessageRepository repository) {
        this.repository = repository;
    }

    /**
     * Executes the given action if and only if this message has not yet been processed
     * by the specified consumer group.
     *
     * @param messageId unique message/event id
     * @param consumerGroup consumer group name
     * @param eventType event type name
     * @param action business logic to execute
     * @return true if executed, false if skipped due to duplicate delivery
     */
    @Transactional
    public boolean executeIfNotProcessed(UUID messageId, String consumerGroup, String eventType, Runnable action) {
        String msgId = messageId.toString();
        if (repository.existsByMessageIdAndConsumerGroup(msgId, consumerGroup)) {
            log.info("Message [{}] already processed by consumer group [{}], skipping duplicate", msgId, consumerGroup);
            return false;
        }

        action.run();

        repository.save(new ConsumedMessageEntity(msgId, consumerGroup, eventType, Instant.now()));
        log.debug("Marked message [{}] as processed for consumer group [{}]", msgId, consumerGroup);
        return true;
    }
}
