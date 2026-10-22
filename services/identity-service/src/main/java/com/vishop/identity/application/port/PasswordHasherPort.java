package com.vishop.identity.application.port;

/**
 * Outbound Port for password hashing and verification.
 * Decouples application logic from specific hashing libraries (e.g. BCrypt, Argon2).
 */
public interface PasswordHasherPort {

    String encode(CharSequence rawPassword);

    boolean matches(CharSequence rawPassword, String encodedPassword);
}
