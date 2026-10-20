package io.github.coreplatform.common.persistence;

import io.github.coreplatform.common.events.EventEnvelope;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Transactional outbox record. Inserted in the exact same transaction as aggregate mutations.
 * Polled or relayed asynchronously to Kafka to achieve reliable distributed messaging.
 */
@Entity
@Table(name = "outbox_events")
public class OutboxEventEntity {

    @Id
    private UUID id;

    @Column(name = "tenant_id")
    private UUID tenantId;

    @Column(name = "event_type", nullable = false, length = 200)
    private String eventType;

    @Column(name = "aggregate_type", nullable = false, length = 100)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false)
    private UUID aggregateId;

    @Column(name = "payload", nullable = false, columnDefinition = "jsonb")
    private String payload;

    @Column(name = "status", nullable = false, length = 30)
    private String status;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    protected OutboxEventEntity() {
    }

    public OutboxEventEntity(UUID id, UUID tenantId, String eventType, String aggregateType,
                             UUID aggregateId, String payload, Instant occurredAt) {
        this.id = id;
        this.tenantId = tenantId;
        this.eventType = eventType;
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.payload = payload;
        this.status = "PENDING";
        this.retryCount = 0;
        this.occurredAt = occurredAt;
    }

    public static OutboxEventEntity fromEnvelope(EventEnvelope envelope, String jsonPayload) {
        return new OutboxEventEntity(
                envelope.eventId(),
                envelope.tenantId(),
                envelope.eventType(),
                envelope.aggregateType(),
                envelope.aggregateId(),
                jsonPayload,
                envelope.occurredAt()
        );
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public String getEventType() { return eventType; }
    public String getAggregateType() { return aggregateType; }
    public UUID getAggregateId() { return aggregateId; }
    public String getPayload() { return payload; }
    public String getStatus() { return status; }
    public int getRetryCount() { return retryCount; }
    public String getErrorMessage() { return errorMessage; }
    public Instant getOccurredAt() { return occurredAt; }
    public Instant getPublishedAt() { return publishedAt; }

    public void markPublished(Instant at) {
        this.status = "PUBLISHED";
        this.publishedAt = at;
    }

    public void recordFailure(String error, int maxRetries) {
        this.retryCount++;
        this.errorMessage = error != null && error.length() > 950 ? error.substring(0, 950) : error;
        if (this.retryCount >= maxRetries) {
            this.status = "FAILED";
        }
    }
}
