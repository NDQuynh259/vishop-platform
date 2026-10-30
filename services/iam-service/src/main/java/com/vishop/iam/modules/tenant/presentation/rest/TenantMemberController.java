package com.vishop.iam.modules.tenant.presentation.rest;

import com.vishop.iam.modules.tenant.application.dto.MemberView;
import com.vishop.iam.modules.tenant.application.dto.TenantMemberResponseDto;
import com.vishop.iam.modules.tenant.application.query.member.GetTenantMemberQuery;
import com.vishop.iam.modules.tenant.application.query.member.ListTenantMembersQuery;
import com.vishop.iam.shared.application.dto.AuthenticatedUser;
import io.github.coreplatform.common.domain.constants.ApiPaths;
import io.github.coreplatform.common.domain.cqrs.QueryBus;
import java.util.List;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(ApiPaths.V1 + "/tenants")
public class TenantMemberController {
  private final QueryBus queries;

  public TenantMemberController(QueryBus queries) {
    this.queries = queries;
  }

  @GetMapping("/{id}/members/me")
  public ResponseEntity<TenantMemberResponseDto> me(
      @AuthenticationPrincipal AuthenticatedUser actor, @PathVariable UUID id) {
    return memberResponse(new GetTenantMemberQuery(actor, id, actor.userId()));
  }

  @GetMapping("/{id}/members")
  public List<MemberView> members(
      @AuthenticationPrincipal AuthenticatedUser actor, @PathVariable UUID id) {
    return queries.dispatch(new ListTenantMembersQuery(actor, id));
  }

  @GetMapping("/{id}/members/{user}")
  public ResponseEntity<TenantMemberResponseDto> member(
      @AuthenticationPrincipal AuthenticatedUser actor,
      @PathVariable UUID id,
      @PathVariable UUID user) {
    return memberResponse(new GetTenantMemberQuery(actor, id, user));
  }

  private ResponseEntity<TenantMemberResponseDto> memberResponse(GetTenantMemberQuery query) {
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(queries.dispatch(query));
  }
}
