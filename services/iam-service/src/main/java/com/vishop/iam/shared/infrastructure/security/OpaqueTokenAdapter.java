package com.vishop.iam.shared.infrastructure.security;

import com.vishop.iam.shared.application.port.OpaqueTokenPort;
import org.springframework.stereotype.Component;

@Component
public class OpaqueTokenAdapter implements OpaqueTokenPort {
  public String generate() {
    return OpaqueTokens.generate();
  }

  public String hash(String raw) {
    return OpaqueTokens.hash(raw);
  }
}
