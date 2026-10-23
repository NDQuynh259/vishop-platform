package com.vishop.identity.domain.entities;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.coreplatform.common.domain.DomainEvent;
import com.vishop.identity.domain.entities.User;
import com.vishop.identity.domain.event.UserLoggedInEvent;
import com.vishop.identity.domain.event.UserRegisteredEvent;
import com.vishop.identity.domain.event.UserStatusChangedEvent;
import com.vishop.identity.domain.valueobjects.PasswordHash;
import com.vishop.identity.domain.valueobjects.UserStatus;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class UserTest {

    @Test
    @DisplayName("Should create User aggregate and emit UserRegisteredEvent")
    void shouldCreateUserAndEmitEvent() {
        UUID tenantId = UUID.randomUUID();
        User user = User.create(
                tenantId,
                "admin@vishop.vn",
                "$2a$10$hashedPasswordValue",
                "Admin ViShop",
                "OWNER"
        );

        assertThat(user.getId()).isNotNull();
        assertThat(user.getTenantId()).isEqualTo(tenantId);
        assertThat(user.getEmail()).isEqualTo("admin@vishop.vn");
        assertThat(user.getFullName()).isEqualTo("Admin ViShop");
        assertThat(user.getRole()).isEqualTo("OWNER");
        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(user.isActive()).isTrue();

        List<DomainEvent> events = user.pullDomainEvents();
        assertThat(events).hasSize(1);
        DomainEvent event = events.get(0);
        assertThat(event).isInstanceOf(UserRegisteredEvent.class);
        UserRegisteredEvent registeredEvent = (UserRegisteredEvent) event;
        assertThat(registeredEvent.aggregateId()).isEqualTo(user.getId());
        assertThat(registeredEvent.email()).isEqualTo("admin@vishop.vn");
        assertThat(registeredEvent.eventType()).isEqualTo("identity.user.registered");
    }

    @Test
    @DisplayName("Should record login and emit UserLoggedInEvent")
    void shouldRecordLoginAndEmitEvent() {
        User user = User.create(
                UUID.randomUUID(),
                "staff@vishop.vn",
                "$2a$10$hashedPasswordValue",
                "Staff Member",
                "STAFF"
        );
        user.clearDomainEvents();

        user.recordLogin();

        List<DomainEvent> events = user.pullDomainEvents();
        assertThat(events).hasSize(1);
        assertThat(events.get(0)).isInstanceOf(UserLoggedInEvent.class);
        UserLoggedInEvent loggedIn = (UserLoggedInEvent) events.get(0);
        assertThat(loggedIn.email()).isEqualTo("staff@vishop.vn");
    }

    @Test
    @DisplayName("Should change user status and emit UserStatusChangedEvent")
    void shouldChangeStatusAndEmitEvent() {
        User user = User.create(
                UUID.randomUUID(),
                "test@vishop.vn",
                "$2a$10$hashedPasswordValue",
                "Test User",
                "STAFF"
        );
        user.clearDomainEvents();

        user.changeStatus(UserStatus.SUSPENDED, "Security policy violation");

        assertThat(user.getStatus()).isEqualTo(UserStatus.SUSPENDED);
        assertThat(user.isActive()).isFalse();

        List<DomainEvent> events = user.pullDomainEvents();
        assertThat(events).hasSize(1);
        assertThat(events.get(0)).isInstanceOf(UserStatusChangedEvent.class);
        UserStatusChangedEvent statusEvent = (UserStatusChangedEvent) events.get(0);
        assertThat(statusEvent.previousStatus()).isEqualTo("ACTIVE");
        assertThat(statusEvent.newStatus()).isEqualTo("SUSPENDED");
        assertThat(statusEvent.reason()).isEqualTo("Security policy violation");
    }

    @Test
    @DisplayName("Should change password hash successfully")
    void shouldChangePassword() {
        User user = User.create(
                UUID.randomUUID(),
                "test@vishop.vn",
                "$2a$10$oldHash",
                "Test User",
                "STAFF"
        );

        user.changePassword(PasswordHash.of("$2a$10$newHashValue"));

        assertThat(user.getPasswordHash()).isEqualTo("$2a$10$newHashValue");
    }
}
