package com.vishop.common.saga.persistence;

import com.vishop.common.saga.SagaStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity tracking the execution state and compensation history of a Saga instance.
 */
@Entity
@Table(name = "saga_instances")
public class SagaInstanceEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "saga_type", nullable = false, length = 100)
    private String sagaType;

    @Column(name = "correlation_id")
    private UUID correlationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private SagaStatus status;

    @Column(name = "current_step_index", nullable = false)
    private int currentStepIndex;

    @Column(name = "current_step_name", length = 150)
    private String currentStepName;

    @Column(name = "payload", columnDefinition = "jsonb")
    private String payload;

    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    protected SagaInstanceEntity() {
    }

    public SagaInstanceEntity(UUID id, String sagaType, UUID correlationId,
                              SagaStatus status, int currentStepIndex, String currentStepName,
                              String payload, Instant startedAt) {
        this.id = id;
        this.sagaType = sagaType;
        this.correlationId = correlationId;
        this.status = status;
        this.currentStepIndex = currentStepIndex;
        this.currentStepName = currentStepName;
        this.payload = payload;
        this.startedAt = startedAt;
        this.updatedAt = startedAt;
    }

    public void updateProgress(int stepIndex, String stepName, SagaStatus status, String payload) {
        this.currentStepIndex = stepIndex;
        this.currentStepName = stepName;
        this.status = status;
        this.payload = payload;
        this.updatedAt = Instant.now();
    }

    public void markCompleted(String payload) {
        this.status = SagaStatus.COMPLETED;
        this.payload = payload;
        this.updatedAt = Instant.now();
        this.completedAt = this.updatedAt;
    }

    public void markCompensating(String error) {
        this.status = SagaStatus.COMPENSATING;
        this.errorMessage = error != null && error.length() > 950 ? error.substring(0, 950) : error;
        this.updatedAt = Instant.now();
    }

    public void markCompensated() {
        this.status = SagaStatus.COMPENSATED;
        this.updatedAt = Instant.now();
        this.completedAt = this.updatedAt;
    }

    public void markFailed(String error) {
        this.status = SagaStatus.FAILED;
        this.errorMessage = error != null && error.length() > 950 ? error.substring(0, 950) : error;
        this.updatedAt = Instant.now();
        this.completedAt = this.updatedAt;
    }

    public UUID getId() { return id; }
    public String getSagaType() { return sagaType; }
    public UUID getCorrelationId() { return correlationId; }
    public SagaStatus getStatus() { return status; }
    public int getCurrentStepIndex() { return currentStepIndex; }
    public String getCurrentStepName() { return currentStepName; }
    public String getPayload() { return payload; }
    public String getErrorMessage() { return errorMessage; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getCompletedAt() { return completedAt; }
}
