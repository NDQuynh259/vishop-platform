package io.github.coreplatform.common.web.exception;

/**
 * Represents an individual field or parameter validation failure.
 */
public record ValidationError(String field, String message) {}
