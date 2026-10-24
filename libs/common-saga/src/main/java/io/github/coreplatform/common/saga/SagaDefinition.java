package io.github.coreplatform.common.saga;

import java.util.List;

/**
 * Definition of a Saga workflow consisting of an ordered list of steps.
 *
 * @param <T> context data type
 */
public interface SagaDefinition<T> {

  // #region sagaType
  /** Unique identifier for the type of saga (e.g. "CreateOrderSaga", "TransferTenantSaga"). */
  String sagaType();

  // #endregion

  // #region steps
  /** Ordered list of steps to execute sequentially. */
  List<SagaStep<T>> steps();
  // #endregion
}
