package com.vishop.identity.domain.ports;

import com.vishop.identity.domain.entities.User;
import com.vishop.identity.domain.valueobjects.Email;
import java.util.Optional;
import java.util.UUID;

/**
 * Domain Repository Port for User Entity.
 */
public interface UserRepositoryPort {

    Optional<User> findById(UUID id);

    Optional<User> findByEmail(String email);

    default Optional<User> findByEmail(Email email) {
        return findByEmail(email.value());
    }

    boolean existsByEmail(String email);

    default boolean existsByEmail(Email email) {
        return existsByEmail(email.value());
    }

    User save(User user);

    void deleteById(UUID id);
}
