package io.github.coreplatform.common.saga;

/**
 * Interface representing an individual step in a Saga workflow.
 * Each step provides a forward execution method and a compensating action.
 *
 * @param <T> context/payload data type passed across steps
 */
public interface SagaStep<T> {

    // #region name
    /**
     * Unique name identifying this step.
     */
    String name();
    // #endregion

    // #region execute
    /**
     * Executes the forward business logic of this step.
     *
     * @param context shared saga context
     * @throws Exception if execution fails, triggering compensation
     */
    void execute(T context) throws Exception;
    // #endregion

    // #region compensate
    /**
     * Executes the compensating (rollback) action if a subsequent step in the saga fails.
     * Must be idempotent and resilient to retries.
     *
     * @param context shared saga context
     */
    void compensate(T context);
    // #endregion
}
