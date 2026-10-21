package io.github.coreplatform.common.domain.cqrs;

/**
 * Dispatcher interface for CQRS commands.
 * Routes commands to their corresponding CommandHandler.
 */
public interface CommandBus {

    // #region dispatch
    /**
     * Dispatches a command to its registered handler.
     *
     * @param command command to execute
     * @param <R> return type
     * @return execution result
     */
    <R> R dispatch(Command<R> command);
    // #endregion
}
