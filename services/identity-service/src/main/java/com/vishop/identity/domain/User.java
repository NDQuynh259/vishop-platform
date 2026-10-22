package com.vishop.identity.domain;

import com.vishop.identity.domain.valueobject.Email;
import com.vishop.identity.domain.valueobject.PasswordHash;
import com.vishop.identity.domain.valueobject.UserRole;
import com.vishop.identity.domain.valueobject.UserStatus;
import java.time.Instant;
import java.util.UUID;

/**
 * Backward compatibility alias for {@link com.vishop.identity.domain.aggregate.User}.
 */
public class User extends com.vishop.identity.domain.aggregate.User {

    public User(
            UUID id,
            UUID tenantId,
            String email,
            String passwordHash,
            String fullName,
            String role,
            UserStatus status,
            Instant createdAt,
            Instant updatedAt,
            Long version) {
        super(
                id,
                tenantId,
                Email.of(email),
                PasswordHash.of(passwordHash),
                fullName,
                UserRole.fromString(role),
                status,
                createdAt,
                updatedAt,
                version
        );
    }

    public User(
            UUID id,
            UUID tenantId,
            String email,
            String passwordHash,
            String fullName,
            String role,
            com.vishop.identity.domain.UserStatus status,
            Instant createdAt,
            Instant updatedAt,
            Long version) {
        this(
                id,
                tenantId,
                email,
                passwordHash,
                fullName,
                role,
                status != null ? UserStatus.valueOf(status.name()) : UserStatus.ACTIVE,
                createdAt,
                updatedAt,
                version
        );
    }

    public static User create(
            UUID tenantId,
            String email,
            String passwordHash,
            String fullName,
            String role) {
        com.vishop.identity.domain.aggregate.User u =
                com.vishop.identity.domain.aggregate.User.create(tenantId, email, passwordHash, fullName, role);
        return new User(
                u.getId(),
                u.getTenantId(),
                u.getEmail(),
                u.getPasswordHash(),
                u.getFullName(),
                u.getRole(),
                u.getStatus(),
                u.getCreatedAt(),
                u.getUpdatedAt(),
                u.getVersion()
        );
    }
}
