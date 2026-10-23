package io.github.coreplatform.common.domain.cqrs;

/**
 * Marker interface for all CQRS commands (write operations). Commands represent intent to change
 * state and may return a result (e.g. created ID or DTO).
 *
 * @param <R> return type of the command
 */
public interface Command<R> {}
