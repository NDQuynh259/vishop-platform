package com.vishop.iam.access.application.dto;

import java.util.UUID;

public record MyTenant(
    UUID id, String code, String name, UUID roleId, String role, String status) {}
