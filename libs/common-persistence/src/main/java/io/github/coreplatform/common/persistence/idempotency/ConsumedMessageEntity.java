package io.github.coreplatform.common.persistence.idempotency;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Tracks consumed Kafka messages to guarantee idempotent processing in consumers. */
@Entity
@Table(name = "consumed_messages")
@IdClass(ConsumedMessageId.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class ConsumedMessageEntity {

  @Id
  @Column(name = "message_id", nullable = false, length = 100)
  private String messageId;

  @Id
  @Column(name = "consumer_group", nullable = false, length = 100)
  private String consumerGroup;

  @Column(name = "event_type", nullable = false, length = 200)
  private String eventType;

  @Column(name = "processed_at", nullable = false)
  private Instant processedAt;
}
