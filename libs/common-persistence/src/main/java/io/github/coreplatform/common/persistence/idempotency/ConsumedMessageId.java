package io.github.coreplatform.common.persistence.idempotency;

import java.io.Serializable;
import java.util.Objects;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Composite primary key for idempotent consumed message tracking. */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class ConsumedMessageId implements Serializable {

  private String messageId;
  private String consumerGroup;

  // #region equals
  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    ConsumedMessageId that = (ConsumedMessageId) o;
    return Objects.equals(messageId, that.messageId)
        && Objects.equals(consumerGroup, that.consumerGroup);
  }

  // #endregion

  // #region hashCode
  @Override
  public int hashCode() {
    return Objects.hash(messageId, consumerGroup);
  }
  // #endregion
}
