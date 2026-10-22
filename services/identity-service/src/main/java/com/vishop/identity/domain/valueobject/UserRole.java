package com.vishop.identity.domain.valueobject;

/**
 * Value Object representing the authorization role of a User within a tenant.
 */
public enum UserRole {
    OWNER,
    ADMIN,
    STAFF;

    public static UserRole fromString(String role) {
        if (role == null || role.isBlank()) {
            return STAFF;
        }
        try {
            return UserRole.valueOf(role.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return STAFF;
        }
    }
}
