package com.vishop.iam.modules.identity.application.port;

import com.vishop.iam.modules.identity.application.dto.UserView;
import io.github.coreplatform.common.domain.pagination.ListPagination;
import io.github.coreplatform.common.domain.pagination.PagedResult;

public interface UserReadPort {
  PagedResult<UserView> users(ListPagination pagination);
}
