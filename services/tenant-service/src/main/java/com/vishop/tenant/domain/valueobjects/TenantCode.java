package com.vishop.tenant.domain.valueobjects;

import io.github.coreplatform.common.domain.ValueObject;
import io.github.coreplatform.common.domain.exception.DomainException;
import java.util.regex.Pattern;

/**
 * Value Object representing a validated tenant unique slug/code.
 * Must be 3-50 lowercase alphanumeric characters or hyphens.
 */
public record TenantCode(String value) implements ValueObject {

    private static final Pattern CODE_PATTERN = Pattern.compile("^[a-z0-9][a-z0-9-]{1,48}[a-z0-9]$");

    // #region TenantCode
    public TenantCode {
        if (value == null || value.isBlank()) {
            throw new DomainException("invalid_tenant_code", "Tenant code cannot be empty");
        }
        String normalized = value.trim().toLowerCase();
        if (!CODE_PATTERN.matcher(normalized).matches()) {
            throw new DomainException("invalid_tenant_code",
                    "Tenant code must be 3-50 lowercase alphanumeric characters and hyphens: " + value);
        }
        value = normalized;
    }
    // #endregion

    // #region of
    public static TenantCode of(String value) {
        return new TenantCode(value);
    }
    // #endregion

    // #region toString
    @Override
    public String toString() {
        return value;
    }
    // #endregion
}
