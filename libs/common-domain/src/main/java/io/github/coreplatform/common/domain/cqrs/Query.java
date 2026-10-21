package io.github.coreplatform.common.domain.cqrs;

/**
 * Marker interface for all CQRS queries (read operations).
 * Queries are strictly read-only and return a projection or DTO.
 *
 * @param <R> return type of the query
 */
public interface Query<R> {
}
