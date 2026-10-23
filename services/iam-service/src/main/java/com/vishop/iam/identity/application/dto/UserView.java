package com.vishop.iam.identity.application.dto;

import com.vishop.iam.identity.domain.valueobjects.SystemRole;
import java.time.Instant;
import java.util.UUID;

public record UserView(
    UUID id,
    String email,
    String fullName,
    SystemRole systemRole,
    String status,
    Instant createdAt) {
  public static UserView from(com.vishop.iam.identity.domain.entities.User user) {
    return new UserView(
        user.id(),
        user.email(),
        user.fullName(),
        user.systemRole(),
        user.status(),
        user.createdAt());
  }
}
