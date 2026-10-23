package com.vishop.iam.shared.application.port;

public interface OpaqueTokenPort {
  String generate();

  String hash(String raw);
}
