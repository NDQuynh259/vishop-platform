package io.github.coreplatform.common.saga;

/** Exception thrown when a Saga workflow encounters a failure. */
public class SagaException extends RuntimeException {

  private final String sagaType;
  private final String stepName;
  private final boolean compensationSuccessful;

  // #region SagaException
  public SagaException(
      String sagaType,
      String stepName,
      String message,
      Throwable cause,
      boolean compensationSuccessful) {
    super(
        String.format(
            "Saga [%s] failed at step [%s] (compensation %s): %s",
            sagaType, stepName, compensationSuccessful ? "succeeded" : "failed", message),
        cause);
    this.sagaType = sagaType;
    this.stepName = stepName;
    this.compensationSuccessful = compensationSuccessful;
  }

  // #endregion

  // #region getSagaType
  public String getSagaType() {
    return sagaType;
  }

  // #endregion
  // #region getStepName
  public String getStepName() {
    return stepName;
  }

  // #endregion
  // #region isCompensationSuccessful
  public boolean isCompensationSuccessful() {
    return compensationSuccessful;
  }
  // #endregion
}
