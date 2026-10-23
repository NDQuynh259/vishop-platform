package com.vishop.iam.identity.application.port;

import com.vishop.iam.identity.application.dto.UserView;
import java.util.List;

public interface UserReadPort {
  List<UserView> users(int limit, int offset);
}
