package com.vishop.iam.modules.access.application.query;

import com.vishop.iam.contracts.identity.IdentityApi;
import com.vishop.iam.modules.access.application.dto.MyTenant;
import com.vishop.iam.modules.access.application.port.AccessReadPort;
import io.github.coreplatform.common.domain.cqrs.QueryHandler;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class ListMyTenantsQueryHandler implements QueryHandler<ListMyTenantsQuery, List<MyTenant>> {
  private final IdentityApi identities;
  private final AccessReadPort iamRead;

  public ListMyTenantsQueryHandler(IdentityApi identities, AccessReadPort iamRead) {
    this.identities = identities;
    this.iamRead = iamRead;
  }

  @Override
  @Transactional(readOnly = true)
  public List<MyTenant> handle(ListMyTenantsQuery query) {
    var actor = query.actor();

    identities.requireActive(actor.userId());
    return iamRead.myTenants(actor.userId());
  }
}
