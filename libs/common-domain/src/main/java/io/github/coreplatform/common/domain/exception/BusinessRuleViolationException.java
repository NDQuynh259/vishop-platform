package io.github.coreplatform.common.domain.exception;

import io.github.coreplatform.common.domain.BusinessRule;

/**
 * Thrown when a business rule or invariant check fails.
 * Typically mapped to HTTP 422 Unprocessable Entity.
 */
public class BusinessRuleViolationException extends DomainException {

    // #region BusinessRuleViolationException
    public BusinessRuleViolationException(String message) {
        super("business_rule_violation", message);
    }
    // #endregion

    // #region BusinessRuleViolationException
    public BusinessRuleViolationException(String code, String message) {
        super(code, message);
    }
    // #endregion

    // #region BusinessRuleViolationException
    public BusinessRuleViolationException(BusinessRule rule) {
        super(rule.getCode(), rule.getMessage());
    }
    // #endregion
}
