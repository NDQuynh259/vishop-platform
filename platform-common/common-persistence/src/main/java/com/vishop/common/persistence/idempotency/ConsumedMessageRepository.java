package com.vishop.common.persistence.idempotency;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ConsumedMessageRepository extends JpaRepository<ConsumedMessageEntity, ConsumedMessageId> {

    boolean existsByMessageIdAndConsumerGroup(String messageId, String consumerGroup);
}
