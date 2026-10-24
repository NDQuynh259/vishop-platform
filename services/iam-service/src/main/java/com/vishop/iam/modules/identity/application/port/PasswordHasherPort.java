package com.vishop.iam.modules.identity.application.port;

public interface PasswordHasherPort {
  String encode(String raw);

  boolean matches(String raw, String encoded);
}
