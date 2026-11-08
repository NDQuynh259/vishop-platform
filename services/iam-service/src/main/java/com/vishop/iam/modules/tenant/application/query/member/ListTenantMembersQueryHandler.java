package com.vishop.iam.modules.tenant.application.query.member;

import com.vishop.iam.modules.tenant.application.api.TenantApi;
import com.vishop.iam.modules.tenant.application.api.TenantApi.MemberReadPort;
import com.vishop.iam.modules.tenant.application.dto.MemberView;
import io.github.coreplatform.common.domain.cqrs.QueryHandler;
import io.github.coreplatform.common.domain.exception.BadRequestException;
import io.github.coreplatform.common.domain.pagination.ListCursor;
import io.github.coreplatform.common.domain.pagination.PagedResult;
import io.github.coreplatform.common.security.constants.CommonPermissions;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class ListTenantMembersQueryHandler
    implements QueryHandler<ListTenantMembersQuery, PagedResult<MemberView>> {
  private final MemberReadPort iamRead;
  private final TenantApi authorization;

  public ListTenantMembersQueryHandler(MemberReadPort iamRead, TenantApi authorization) {
    this.iamRead = iamRead;
    this.authorization = authorization;
  }

  @Override
  @Transactional(readOnly = true)
  public PagedResult<MemberView> handle(ListTenantMembersQuery query) {
    var actor = query.actor();
    var tenant = query.tenant();

    authorization.requireAccess(actor, tenant, CommonPermissions.Tenant.MEMBER_VIEW);
    var pagination = query.pagination();
    if (pagination.isCursor()) {
      String scope = "tenant-members:" + tenant;
      var keys = ListCursor.decode(pagination.cursor().cursor(), scope, 2);
      UUID after = null;
      if (!keys.isEmpty()) {
        try {
          after = UUID.fromString(keys.get(1));
        } catch (IllegalArgumentException failure) {
          throw new BadRequestException("Invalid member cursor");
        }
      }
      var rows =
          iamRead.members(
              tenant, pagination.limit() + 1, 0, keys.isEmpty() ? null : keys.getFirst(), after);
      boolean hasNext = rows.size() > pagination.limit();
      var page = rows.stream().limit(pagination.limit()).toList();
      String next =
          hasNext
              ? ListCursor.encode(
                  scope, List.of(page.getLast().email(), page.getLast().id().toString()))
              : null;
      return PagedResult.ofCursor(page, hasNext, next);
    }
    var offset = pagination.offset();
    long total = iamRead.memberCount(tenant);
    if (offset.offset() > Integer.MAX_VALUE && offset.offset() < total)
      throw new BadRequestException("Requested pagination offset is too large");
    var items =
        offset.offset() >= total
            ? List.<MemberView>of()
            : iamRead.members(tenant, offset.limit(), (int) offset.offset(), null, null);
    return PagedResult.ofOffset(items, offset.page(), offset.limit(), total);
  }
}
