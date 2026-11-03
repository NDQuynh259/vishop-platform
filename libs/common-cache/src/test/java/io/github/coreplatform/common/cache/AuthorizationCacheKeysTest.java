package io.github.coreplatform.common.cache;

import static org.assertj.core.api.Assertions.*;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class AuthorizationCacheKeysTest {
  @Test
  void preservesTheIamSqlProjectionKeyContract() {
    UUID user = UUID.randomUUID(),
        tenant = UUID.randomUUID(),
        role = UUID.randomUUID(),
        session = UUID.randomUUID();
    assertThat(AuthorizationCacheKeys.user(user)).isEqualTo("auth:user:" + user);
    String member = AuthorizationCacheKeys.member(tenant, user);
    assertThat(member).isEqualTo("auth:member:" + tenant + ":" + user);
    assertThat(AuthorizationCacheKeys.session(session)).isEqualTo("auth:session:" + session);
    assertThat(AuthorizationCacheKeys.tenant(tenant)).isEqualTo("auth:tenant:" + tenant);
    assertThat(AuthorizationCacheKeys.role(tenant, role))
        .isEqualTo("auth:role:" + tenant + ":" + role);
    assertThat(AuthorizationCacheKeys.role(UUID.randomUUID(), role))
        .isNotEqualTo(AuthorizationCacheKeys.role(tenant, role));
  }
}
