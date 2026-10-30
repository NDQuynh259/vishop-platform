package com.vishop.iam.modules.identity.presentation.rest.dto;

import java.util.UUID;

/** tenantId tùy chọn để chọn tenant qua refresh; không tạo thêm endpoint switch-tenant. */
public record RefreshRequest(UUID tenantId) {}
