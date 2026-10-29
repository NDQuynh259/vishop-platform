package com.vishop.iam.modules.tenant.application.port;

import com.vishop.iam.modules.tenant.application.dto.TenantListCriteria;
import com.vishop.iam.modules.tenant.application.dto.TenantResponseDto;
import io.github.coreplatform.common.domain.pagination.CursorParams;
import io.github.coreplatform.common.domain.pagination.OffsetPagination;
import io.github.coreplatform.common.domain.pagination.PagedResult;
import java.util.UUID;

public interface TenantReadPort {
  PagedResult<TenantResponseDto> findByUserId(
      UUID userId, OffsetPagination pagination, TenantListCriteria criteria);

  PagedResult<TenantResponseDto> findByUserIdAfter(
      UUID userId, CursorParams pagination, TenantListCriteria criteria);
}
