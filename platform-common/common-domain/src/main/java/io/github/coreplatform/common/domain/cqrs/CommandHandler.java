package io.github.coreplatform.common.domain.cqrs;

/**
 * Handler interface for CQRS commands.
 *
 * @param <C> command type
 * @param <R> return type
 */
public interface CommandHandler<C extends Command<R>, R> {

    /**
     * Executes the command within an application transaction.
     *
     * @param command the command object
     * @return the command result
     */
    R handle(C command);
}
