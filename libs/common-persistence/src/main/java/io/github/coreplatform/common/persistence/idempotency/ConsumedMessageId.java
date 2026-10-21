package io.github.coreplatform.common.persistence.idempotency;

import java.io.Serializable;
import java.util.Objects;

/**
 * Composite primary key for idempotent consumed message tracking.
 */
public class ConsumedMessageId implements Serializable {

    private String messageId;
    private String consumerGroup;

    // #region ConsumedMessageId
    public ConsumedMessageId() {
    }
    // #endregion

    // #region ConsumedMessageId
    public ConsumedMessageId(String messageId, String consumerGroup) {
        this.messageId = messageId;
        this.consumerGroup = consumerGroup;
    }
    // #endregion

    // #region getMessageId
    public String getMessageId() { return messageId; }
    // #endregion
    // #region getConsumerGroup
    public String getConsumerGroup() { return consumerGroup; }
    // #endregion

    // #region equals
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ConsumedMessageId that = (ConsumedMessageId) o;
        return Objects.equals(messageId, that.messageId) && Objects.equals(consumerGroup, that.consumerGroup);
    }
    // #endregion

    // #region hashCode
    @Override
    public int hashCode() {
        return Objects.hash(messageId, consumerGroup);
    }
    // #endregion
}
