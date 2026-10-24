package com.vishop.iam.modules.tenant.domain.valueobject;

import io.github.coreplatform.common.domain.exception.BadRequestException;

public record TenantCode(String value) {
  public TenantCode {
    if (value == null || !value.matches("^[a-z0-9][a-z0-9-]{1,48}[a-z0-9]$"))
      throw new BadRequestException("Invalid tenant code");
  }
}
