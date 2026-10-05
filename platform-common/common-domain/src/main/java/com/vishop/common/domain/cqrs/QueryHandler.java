package com.vishop.common.domain.cqrs;

/**
 * Handler interface for CQRS queries.
 *
 * @param <Q> query type
 * @param <R> return type
 */
public interface QueryHandler<Q extends Query<R>, R> {

    /**
     * Executes the query and returns the requested data.
     *
     * @param query the query object
     * @return the query result
     */
    R handle(Q query);
}
