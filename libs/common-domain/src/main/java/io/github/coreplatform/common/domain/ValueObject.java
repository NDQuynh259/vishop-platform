package io.github.coreplatform.common.domain;

import java.io.Serializable;

/**
 * Marker interface for DDD Value Objects.
 * Value objects are immutable and defined by their attributes rather than a persistent identity.
 * In modern Java, records are the preferred implementation for Value Objects.
 */
public interface ValueObject extends Serializable {
}
