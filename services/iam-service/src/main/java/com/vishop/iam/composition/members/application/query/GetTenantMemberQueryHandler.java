package com.vishop.iam.composition.members.application.query;

import com.vishop.iam.composition.members.application.port.TenantMemberReadPort;
import com.vishop.iam.modules.access.application.api.AccessApi;
import com.vishop.iam.modules.access.application.dto.TenantMemberResponseDto;
import io.github.coreplatform.common.domain.cqrs.QueryHandler;
import io.github.coreplatform.common.domain.exception.EntityNotFoundException;
import io.github.coreplatform.common.security.constants.CommonPermissions;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Component
public class GetTenantMemberQueryHandler
    implements QueryHandler<GetTenantMemberQuery, TenantMemberResponseDto> {
  private final TenantMemberReadPort reads;
  private final AccessApi authorization;

  public GetTenantMemberQueryHandler(TenantMemberReadPort reads, AccessApi authorization) {
    this.reads = reads;
    this.authorization = authorization;
  }

  @Override
  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public TenantMemberResponseDto handle(GetTenantMemberQuery query) {
    String permission = query.actor().userId().equals(query.user()) ? null : CommonPermissions.Tenant.MEMBER_VIEW;
    authorization.requireAccess(query.actor().userId(), query.tenant(), permission);
    return reads
        .member(query.tenant(), query.user())
        .orElseThrow(() -> new EntityNotFoundException("Member", query.user()));
  }
}
