package io.github.coreplatform.common.domain.exception;

import io.github.coreplatform.common.domain.BusinessRule;

/**
 * Thrown when a business rule or invariant check fails.
 * Typically mapped to HTTP 422 Unprocessable Entity.
 */
public class BusinessRuleViolationException extends DomainException {

    public BusinessRuleViolationException(String code, String message) {
        super(code, message);
    }

    public BusinessRuleViolationException(BusinessRule rule) {
        super(rule.getCode(), rule.getMessage());
    }
}
