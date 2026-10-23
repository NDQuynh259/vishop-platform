package com.vishop.iam.identity.infrastructure.security;

import com.vishop.iam.identity.application.port.PasswordHasherPort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class PasswordHasherAdapter implements PasswordHasherPort {
  private final PasswordEncoder passwords;

  public PasswordHasherAdapter(PasswordEncoder passwords) {
    this.passwords = passwords;
  }

  public String encode(String raw) {
    return passwords.encode(raw);
  }

  public boolean matches(String raw, String encoded) {
    return passwords.matches(raw, encoded);
  }
}
