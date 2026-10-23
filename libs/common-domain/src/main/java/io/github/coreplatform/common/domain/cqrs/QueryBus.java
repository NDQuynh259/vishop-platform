package io.github.coreplatform.common.domain.cqrs;

/** Dispatcher interface for CQRS queries. Routes queries to their corresponding QueryHandler. */
public interface QueryBus {

  // #region dispatch
  /**
   * Dispatches a query to its registered handler.
   *
   * @param query query to execute
   * @param <R> return type
   * @return query result
   */
  <R> R dispatch(Query<R> query);
  // #endregion
}
