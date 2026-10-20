package io.github.coreplatform.common.domain.exception;

/**
 * Thrown when an aggregate root or domain entity cannot be found.
 * Typically mapped to HTTP 404 Not Found.
 */
public class EntityNotFoundException extends DomainException {

    private final String entityType;
    private final Object entityId;

    public EntityNotFoundException(String entityType, Object entityId) {
        super("entity_not_found", String.format("%s with id '%s' was not found", entityType, entityId));
        this.entityType = entityType;
        this.entityId = entityId;
    }

    public String getEntityType() {
        return entityType;
    }

    public Object getEntityId() {
        return entityId;
    }
}
