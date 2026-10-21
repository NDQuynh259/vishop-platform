package io.github.coreplatform.common.domain;

import java.util.Optional;

/**
 * Generic DDD Repository port for Aggregate Roots.
 * Domain repositories are defined in the domain layer as interfaces and implemented
 * by infrastructure adapters (e.g. Spring Data JPA, jOOQ).
 *
 * @param <AR> type of AggregateRoot
 * @param <ID> type of identifier
 */
public interface DomainRepository<AR extends AggregateRoot<ID>, ID> {

    // #region save
    /**
     * Persists the given aggregate root (insert or update).
     *
     * @param aggregate aggregate to save
     * @return saved aggregate
     */
    AR save(AR aggregate);
    // #endregion

    // #region findById
    /**
     * Finds an aggregate root by its identifier.
     *
     * @param id identifier
     * @return aggregate if found
     */
    Optional<AR> findById(ID id);
    // #endregion

    // #region delete
    /**
     * Deletes the given aggregate root.
     *
     * @param aggregate aggregate to delete
     */
    void delete(AR aggregate);
    // #endregion

    // #region existsById
    /**
     * Checks if an aggregate with the given identifier exists.
     *
     * @param id identifier
     * @return true if exists
     */
    boolean existsById(ID id);
    // #endregion
}
