package com.vishop.iam.modules.identity.application.port;

import com.vishop.iam.modules.identity.application.dto.UserView;
import java.util.List;

public interface UserReadPort {
  List<UserView> users(int limit, int offset);
}
