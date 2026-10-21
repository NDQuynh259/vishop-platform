package com.vishop.audit.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "audit_logs")
public class AuditLogEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    @Column(name = "event_type", nullable = false, length = 200)
    private String eventType;

    @Column(name = "tenant_id")
    private UUID tenantId;

    @Column(name = "aggregate_type", nullable = false, length = 100)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false)
    private UUID aggregateId;

    @Column(name = "producer", length = 100)
    private String producer;

    @Column(name = "payload", nullable = false, columnDefinition = "jsonb")
    private String payload;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    // #region AuditLogEntity
    protected AuditLogEntity() {
    }
    // #endregion

    // #region AuditLogEntity
    public AuditLogEntity(UUID id, UUID eventId, String eventType, UUID tenantId,
                          String aggregateType, UUID aggregateId, String producer,
                          String payload, Instant occurredAt, Instant recordedAt) {
        this.id = id;
        this.eventId = eventId;
        this.eventType = eventType;
        this.tenantId = tenantId;
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.producer = producer;
        this.payload = payload;
        this.occurredAt = occurredAt;
        this.recordedAt = recordedAt;
    }
    // #endregion

    // #region getId
    public UUID getId() { return id; }
    // #endregion
    // #region getEventId
    public UUID getEventId() { return eventId; }
    // #endregion
    // #region getEventType
    public String getEventType() { return eventType; }
    // #endregion
    // #region getTenantId
    public UUID getTenantId() { return tenantId; }
    // #endregion
    // #region getAggregateType
    public String getAggregateType() { return aggregateType; }
    // #endregion
    // #region getAggregateId
    public UUID getAggregateId() { return aggregateId; }
    // #endregion
    // #region getProducer
    public String getProducer() { return producer; }
    // #endregion
    // #region getPayload
    public String getPayload() { return payload; }
    // #endregion
    // #region getOccurredAt
    public Instant getOccurredAt() { return occurredAt; }
    // #endregion
    // #region getRecordedAt
    public Instant getRecordedAt() { return recordedAt; }
    // #endregion
}
