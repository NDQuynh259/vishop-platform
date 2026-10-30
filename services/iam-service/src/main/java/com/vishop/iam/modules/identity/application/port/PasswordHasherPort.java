package com.vishop.iam.modules.identity.application.port;

public interface PasswordHasherPort {
  String encode(String password);

  boolean matches(String password, String hash);
}
