package com.vishop.iam.composition.members.presentation.rest;

import com.vishop.iam.composition.members.application.query.GetTenantMemberQuery;
import com.vishop.iam.infra.security.IamPrincipal;
import com.vishop.iam.modules.access.application.dto.TenantMemberResponseDto;
import io.github.coreplatform.common.domain.constants.ApiPaths;
import io.github.coreplatform.common.domain.cqrs.QueryBus;
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
      @AuthenticationPrincipal IamPrincipal actor, @PathVariable UUID id) {
    return memberResponse(new GetTenantMemberQuery(actor.actor(), id, actor.userId()));
  }

  @GetMapping("/{id}/members/{user}")
  public ResponseEntity<TenantMemberResponseDto> member(
      @AuthenticationPrincipal IamPrincipal actor, @PathVariable UUID id, @PathVariable UUID user) {
    return memberResponse(new GetTenantMemberQuery(actor.actor(), id, user));
  }

  private ResponseEntity<TenantMemberResponseDto> memberResponse(GetTenantMemberQuery query) {
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(queries.dispatch(query));
  }
}
