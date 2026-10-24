package com.vishop.iam.modules.identity.domain.service;

import io.github.coreplatform.common.domain.exception.BadRequestException;
import java.nio.charset.StandardCharsets;

public final class PasswordPolicy {
  private PasswordPolicy() {}

  public static void validate(String password) {
    if (password == null
        || password.getBytes(StandardCharsets.UTF_8).length > 72
        || password.length() < 8
        || !password.matches("(?s).*[A-Z].*")
        || !password.matches("(?s).*[a-z].*")
        || !password.matches("(?s).*[0-9].*")
        || !password.matches("(?s).*[^a-zA-Z0-9].*"))
      throw new BadRequestException(
          "Password needs upper/lowercase letters, a digit, a symbol and 8Ã¢â‚¬â€œ72 UTF-8 bytes");
  }
}
