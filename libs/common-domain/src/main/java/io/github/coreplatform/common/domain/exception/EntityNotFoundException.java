package io.github.coreplatform.common.domain.exception;

/**
 * Thrown when an aggregate root or domain entity cannot be found.
 * Typically mapped to HTTP 404 Not Found.
 */
public class EntityNotFoundException extends DomainException {

    private final String entityType;
    private final Object entityId;

    // #region EntityNotFoundException
    public EntityNotFoundException(String entityType, Object entityId) {
        super("entity_not_found", String.format("%s with id '%s' was not found", entityType, entityId));
        this.entityType = entityType;
        this.entityId = entityId;
    }
    // #endregion

    // #region EntityNotFoundException
    public EntityNotFoundException(String message) {
        super("entity_not_found", message);
        this.entityType = null;
        this.entityId = null;
    }
    // #endregion

    // #region EntityNotFoundException
    public EntityNotFoundException(String entityType, Object entityId, String message) {
        super("entity_not_found", message);
        this.entityType = entityType;
        this.entityId = entityId;
    }
    // #endregion

    // #region getEntityType
    public String getEntityType() {
        return entityType;
    }
    // #endregion

    // #region getEntityId
    public Object getEntityId() {
        return entityId;
    }
    // #endregion
}
