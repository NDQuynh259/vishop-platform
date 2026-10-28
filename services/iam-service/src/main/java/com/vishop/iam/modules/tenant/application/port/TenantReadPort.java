package com.vishop.iam.modules.tenant.application.port;

import com.vishop.iam.modules.tenant.application.dto.TenantListCriteria;
import com.vishop.iam.modules.tenant.application.dto.TenantResponseDto;
import io.github.coreplatform.common.domain.pagination.OffsetPagination;
import io.github.coreplatform.common.domain.pagination.PagedResult;
import java.util.List;
import java.util.UUID;

public interface TenantReadPort {
  PagedResult<TenantResponseDto> findByTenantIds(
      List<UUID> tenantIds, OffsetPagination pagination, TenantListCriteria criteria);
}
