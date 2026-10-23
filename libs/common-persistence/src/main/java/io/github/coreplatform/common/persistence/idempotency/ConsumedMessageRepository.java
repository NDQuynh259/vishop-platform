package io.github.coreplatform.common.persistence.idempotency;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ConsumedMessageRepository
    extends JpaRepository<ConsumedMessageEntity, ConsumedMessageId> {

  // #region existsByMessageIdAndConsumerGroup
  boolean existsByMessageIdAndConsumerGroup(String messageId, String consumerGroup);
  // #endregion
}
