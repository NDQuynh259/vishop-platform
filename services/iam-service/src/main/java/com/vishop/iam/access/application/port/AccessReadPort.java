package com.vishop.iam.access.application.port;

import com.vishop.iam.access.application.dto.MemberView;
import com.vishop.iam.access.application.dto.MyTenant;
import java.util.List;
import java.util.UUID;

public interface AccessReadPort {
  List<MyTenant> myTenants(UUID user);

  List<MemberView> members(UUID tenant);
}
