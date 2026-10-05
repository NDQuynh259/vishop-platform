package com.vishop.common.saga;

/**
 * Exception thrown when a Saga workflow encounters a failure.
 */
public class SagaException extends RuntimeException {

    private final String sagaType;
    private final String stepName;
    private final boolean compensationSuccessful;

    public SagaException(String sagaType, String stepName, String message, Throwable cause, boolean compensationSuccessful) {
        super(String.format("Saga [%s] failed at step [%s] (compensation %s): %s",
                sagaType, stepName, compensationSuccessful ? "succeeded" : "failed", message), cause);
        this.sagaType = sagaType;
        this.stepName = stepName;
        this.compensationSuccessful = compensationSuccessful;
    }

    public String getSagaType() { return sagaType; }
    public String getStepName() { return stepName; }
    public boolean isCompensationSuccessful() { return compensationSuccessful; }
}
