package io.github.coreplatform.common.domain.cqrs;

import io.github.coreplatform.common.domain.pagination.PagedResult;

/**
 * Common interface for CQRS read queries returning paginated results with a filter.
 *
 * @param <F> type of filter/criteria
 * @param <R> type of items in the page
 */
public interface PaginatedQuery<F, R> extends Query<PagedResult<R>> {

    /**
     * Business filter criteria.
     *
     * @return filter object
     */
    F filter();
}
