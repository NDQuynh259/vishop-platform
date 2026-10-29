package com.vishop.iam.modules.tenant.application.query.member;

import com.vishop.iam.modules.tenant.application.api.TenantApi;
import com.vishop.iam.modules.tenant.application.api.TenantApi.MemberReadPort;
import com.vishop.iam.modules.tenant.application.dto.TenantMemberResponseDto;
import io.github.coreplatform.common.domain.cqrs.QueryHandler;
import io.github.coreplatform.common.domain.exception.EntityNotFoundException;
import io.github.coreplatform.common.security.constants.CommonPermissions;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Component
public class GetTenantMemberQueryHandler
    implements QueryHandler<GetTenantMemberQuery, TenantMemberResponseDto> {
  private final MemberReadPort reads;
  private final TenantApi authorization;

  public GetTenantMemberQueryHandler(MemberReadPort reads, TenantApi authorization) {
    this.reads = reads;
    this.authorization = authorization;
  }

  @Override
  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public TenantMemberResponseDto handle(GetTenantMemberQuery query) {
    String permission =
        query.actor().userId().equals(query.user()) ? null : CommonPermissions.Tenant.MEMBER_VIEW;
    authorization.requireAccess(query.actor(), query.tenant(), permission);
    return reads
        .member(query.tenant(), query.user())
        .orElseThrow(() -> new EntityNotFoundException("Member", query.user()));
  }
}
