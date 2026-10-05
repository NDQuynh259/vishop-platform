package com.vishop.common.persistence.idempotency;

import java.io.Serializable;
import java.util.Objects;

/**
 * Composite primary key for idempotent consumed message tracking.
 */
public class ConsumedMessageId implements Serializable {

    private String messageId;
    private String consumerGroup;

    public ConsumedMessageId() {
    }

    public ConsumedMessageId(String messageId, String consumerGroup) {
        this.messageId = messageId;
        this.consumerGroup = consumerGroup;
    }

    public String getMessageId() { return messageId; }
    public String getConsumerGroup() { return consumerGroup; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ConsumedMessageId that = (ConsumedMessageId) o;
        return Objects.equals(messageId, that.messageId) && Objects.equals(consumerGroup, that.consumerGroup);
    }

    @Override
    public int hashCode() {
        return Objects.hash(messageId, consumerGroup);
    }
}
