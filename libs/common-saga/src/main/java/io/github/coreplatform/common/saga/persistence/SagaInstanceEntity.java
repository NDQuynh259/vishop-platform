package io.github.coreplatform.common.saga.persistence;

import io.github.coreplatform.common.saga.SagaStatus;
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

    // #region SagaInstanceEntity
    protected SagaInstanceEntity() {
    }
    // #endregion

    // #region SagaInstanceEntity
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
    // #endregion

    // #region updateProgress
    public void updateProgress(int stepIndex, String stepName, SagaStatus status, String payload) {
        this.currentStepIndex = stepIndex;
        this.currentStepName = stepName;
        this.status = status;
        this.payload = payload;
        this.updatedAt = Instant.now();
    }
    // #endregion

    // #region markCompleted
    public void markCompleted(String payload) {
        this.status = SagaStatus.COMPLETED;
        this.payload = payload;
        this.updatedAt = Instant.now();
        this.completedAt = this.updatedAt;
    }
    // #endregion

    // #region markCompensating
    public void markCompensating(String error) {
        this.status = SagaStatus.COMPENSATING;
        this.errorMessage = error != null && error.length() > 950 ? error.substring(0, 950) : error;
        this.updatedAt = Instant.now();
    }
    // #endregion

    // #region markCompensated
    public void markCompensated() {
        this.status = SagaStatus.COMPENSATED;
        this.updatedAt = Instant.now();
        this.completedAt = this.updatedAt;
    }
    // #endregion

    // #region markFailed
    public void markFailed(String error) {
        this.status = SagaStatus.FAILED;
        this.errorMessage = error != null && error.length() > 950 ? error.substring(0, 950) : error;
        this.updatedAt = Instant.now();
        this.completedAt = this.updatedAt;
    }
    // #endregion

    // #region getId
    public UUID getId() { return id; }
    // #endregion
    // #region getSagaType
    public String getSagaType() { return sagaType; }
    // #endregion
    // #region getCorrelationId
    public UUID getCorrelationId() { return correlationId; }
    // #endregion
    // #region getStatus
    public SagaStatus getStatus() { return status; }
    // #endregion
    // #region getCurrentStepIndex
    public int getCurrentStepIndex() { return currentStepIndex; }
    // #endregion
    // #region getCurrentStepName
    public String getCurrentStepName() { return currentStepName; }
    // #endregion
    // #region getPayload
    public String getPayload() { return payload; }
    // #endregion
    // #region getErrorMessage
    public String getErrorMessage() { return errorMessage; }
    // #endregion
    // #region getStartedAt
    public Instant getStartedAt() { return startedAt; }
    // #endregion
    // #region getUpdatedAt
    public Instant getUpdatedAt() { return updatedAt; }
    // #endregion
    // #region getCompletedAt
    public Instant getCompletedAt() { return completedAt; }
    // #endregion
}
