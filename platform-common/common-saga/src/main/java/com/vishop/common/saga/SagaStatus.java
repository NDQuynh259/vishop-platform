package com.vishop.common.saga;

/**
 * Status of a distributed Saga workflow instance.
 */
public enum SagaStatus {
    STARTED,
    IN_PROGRESS,
    COMPLETED,
    COMPENSATING,
    COMPENSATED,
    FAILED
}
