package com.vishop.iam.modules.identity.infrastructure.security;

import com.vishop.iam.modules.identity.application.port.PasswordHasherPort;
import java.nio.charset.StandardCharsets;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class PasswordHasherAdapter implements PasswordHasherPort {
  private final PasswordEncoder encoder;
  private final String dummyHash;

  public PasswordHasherAdapter(PasswordEncoder encoder) {
    this.encoder = encoder;
    // User không tồn tại vẫn chạy BCrypt một lần, tránh phân biệt email bằng thời
    // gian phản hồi.
    this.dummyHash = encoder.encode("dummy-password-never-used-for-login");
  }

  public String encode(String password) {
    // BCrypt chỉ xử lý tối đa 72 byte, không để hai mật khẩu dài cùng tiền tố bị
    // coi là một.
    if (password == null
        || password.length() < 8
        || password.getBytes(StandardCharsets.UTF_8).length > 72)
      throw new io.github.coreplatform.common.domain.exception.BadRequestException(
          "Password must have at least 8 characters and at most 72 UTF-8 bytes");
    return encoder.encode(password);
  }

  public boolean matches(String password, String hash) {
    if (password == null || password.getBytes(StandardCharsets.UTF_8).length > 72) return false;
    boolean matched = encoder.matches(password, hash == null ? dummyHash : hash);
    return hash != null && matched;
  }
}
