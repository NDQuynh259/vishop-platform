package com.vishop.iam.modules.access.application.dto;

import java.util.UUID;

public record MemberView(
    UUID id, UUID userId, String email, String fullName, UUID roleId, String role, String status) {}
