package com.vishop.identity.domain.aggregate;

import io.github.coreplatform.common.domain.AggregateRoot;
import io.github.coreplatform.common.domain.UuidV7;
import com.vishop.identity.domain.event.UserLoggedInEvent;
import com.vishop.identity.domain.event.UserRegisteredEvent;
import com.vishop.identity.domain.event.UserStatusChangedEvent;
import com.vishop.identity.domain.valueobject.Email;
import com.vishop.identity.domain.valueobject.PasswordHash;
import com.vishop.identity.domain.valueobject.UserId;
import com.vishop.identity.domain.valueobject.UserRole;
import com.vishop.identity.domain.valueobject.UserStatus;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * User Aggregate Root.
 * Manages identity credentials, tenant scoping, lifecycle states, and domain events.
 */
public class User extends AggregateRoot<UUID> {

    private final UUID tenantId;
    private Email email;
    private PasswordHash passwordHash;
    private String fullName;
    private UserRole role;
    private UserStatus status;
    private final Instant createdAt;
    private Instant updatedAt;

    // #region User constructor
    public User(
            UUID id,
            UUID tenantId,
            Email email,
            PasswordHash passwordHash,
            String fullName,
            UserRole role,
            UserStatus status,
            Instant createdAt,
            Instant updatedAt,
            Long version) {
        super(Objects.requireNonNull(id, "User id cannot be null"), version);
        this.tenantId = Objects.requireNonNull(tenantId, "TenantId cannot be null");
        this.email = Objects.requireNonNull(email, "Email cannot be null");
        this.passwordHash = Objects.requireNonNull(passwordHash, "PasswordHash cannot be null");
        this.fullName = Objects.requireNonNull(fullName, "FullName cannot be null").trim();
        this.role = role != null ? role : UserRole.STAFF;
        this.status = status != null ? status : UserStatus.ACTIVE;
        this.createdAt = createdAt != null ? createdAt : Instant.now();
        this.updatedAt = updatedAt != null ? updatedAt : this.createdAt;
    }
    // #endregion

    // #region create
    /**
     * Factory method creating a brand new User aggregate and registering UserRegisteredEvent.
     */
    public static User create(
            UUID tenantId,
            String emailStr,
            String passwordHashStr,
            String fullName,
            String roleStr) {
        UUID id = UuidV7.nextId();
        UUID effectiveTenantId = tenantId != null ? tenantId : UuidV7.nextId();
        Email email = Email.of(emailStr);
        PasswordHash passwordHash = PasswordHash.of(passwordHashStr);
        UserRole role = UserRole.fromString(roleStr);
        Instant now = Instant.now();

        User user = new User(
                id,
                effectiveTenantId,
                email,
                passwordHash,
                fullName,
                role,
                UserStatus.ACTIVE,
                now,
                now,
                0L
        );

        user.registerEvent(UserRegisteredEvent.of(
                id,
                effectiveTenantId,
                email.value(),
                user.fullName,
                role.name()
        ));

        return user;
    }
    // #endregion

    // #region reconstitute
    /**
     * Reconstitutes an existing User aggregate from persistence layer without emitting domain events.
     */
    public static User reconstitute(
            UUID id,
            UUID tenantId,
            String emailStr,
            String passwordHashStr,
            String fullName,
            String roleStr,
            UserStatus status,
            Instant createdAt,
            Instant updatedAt,
            Long version) {
        return new User(
                id,
                tenantId,
                Email.of(emailStr),
                PasswordHash.of(passwordHashStr),
                fullName,
                UserRole.fromString(roleStr),
                status,
                createdAt,
                updatedAt,
                version
        );
    }
    // #endregion

    // #region recordLogin
    /**
     * Records a successful login and registers UserLoggedInEvent.
     */
    public void recordLogin() {
        this.updatedAt = Instant.now();
        registerEvent(UserLoggedInEvent.of(getId(), this.tenantId, this.email.value()));
    }
    // #endregion

    // #region changeStatus
    /**
     * Updates account lifecycle status and registers UserStatusChangedEvent.
     */
    public void changeStatus(UserStatus newStatus, String reason) {
        Objects.requireNonNull(newStatus, "New status cannot be null");
        if (this.status != newStatus) {
            String prev = this.status.name();
            this.status = newStatus;
            this.updatedAt = Instant.now();
            registerEvent(UserStatusChangedEvent.of(getId(), this.tenantId, prev, newStatus.name(), reason));
        }
    }
    // #endregion

    // #region changePassword
    /**
     * Updates password hash.
     */
    public void changePassword(PasswordHash newPasswordHash) {
        this.passwordHash = Objects.requireNonNull(newPasswordHash, "New password hash cannot be null");
        this.updatedAt = Instant.now();
    }
    // #endregion

    // #region updateProfile
    /**
     * Updates user's full name.
     */
    public void updateProfile(String newFullName) {
        if (newFullName != null && !newFullName.isBlank()) {
            this.fullName = newFullName.trim();
            this.updatedAt = Instant.now();
        }
    }
    // #endregion

    public boolean isActive() {
        return this.status == UserStatus.ACTIVE;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public String getEmail() {
        return email.value();
    }

    public Email getEmailVo() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash.value();
    }

    public PasswordHash getPasswordHashVo() {
        return passwordHash;
    }

    public String getFullName() {
        return fullName;
    }

    public String getRole() {
        return role.name();
    }

    public UserRole getRoleVo() {
        return role;
    }

    public UserStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
