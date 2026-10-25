package com.vishop.iam.modules.identity.application.dto;

import java.time.Instant;
import java.util.UUID;

public record UserView(
    UUID id, String email, String fullName, String systemRole, String status, Instant createdAt) {
}
