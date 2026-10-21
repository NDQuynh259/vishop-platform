package io.github.coreplatform.common.domain;

/**
 * Interface representing a domain business rule / invariant.
 * Can be verified in domain entities:
 * <pre>
 *   checkRule(new OrderMustHaveAtLeastOneItemRule(items));
 * </pre>
 */
public interface BusinessRule {

    // #region isBroken
    /**
     * Checks if the rule is violated / broken.
     *
     * @return true if the rule is broken, false if satisfied
     */
    boolean isBroken();
    // #endregion

    // #region getCode
    /**
     * Error code identifying the violated rule.
     *
     * @return snake_case error code
     */
    String getCode();
    // #endregion

    // #region getMessage
    /**
     * Detailed human-readable explanation of why the rule was broken.
     *
     * @return message description
     */
    String getMessage();
    // #endregion
}
