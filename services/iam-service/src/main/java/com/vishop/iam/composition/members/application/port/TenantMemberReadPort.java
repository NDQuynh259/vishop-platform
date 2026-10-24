package com.vishop.iam.composition.members.application.port;

import com.vishop.iam.contracts.access.TenantMemberResponseDto;
import java.util.Optional;
import java.util.UUID;

/** Read projection across identity, tenant and access data inside the IAM database. */
public interface TenantMemberReadPort {
  Optional<TenantMemberResponseDto> member(UUID tenant, UUID user);
}
