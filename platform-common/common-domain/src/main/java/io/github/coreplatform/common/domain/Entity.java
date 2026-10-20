package io.github.coreplatform.common.domain;

import java.util.Objects;

/**
 * Base class for all domain entities characterized by identity.
 *
 * @param <ID> type of identity identifier
 */
public abstract class Entity<ID> {

    protected final ID id;

    protected Entity(ID id) {
        this.id = Objects.requireNonNull(id, "Entity identifier cannot be null");
    }

    public ID getId() {
        return id;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Entity<?> entity = (Entity<?>) o;
        return Objects.equals(id, entity.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
