package io.github.coreplatform.common.cache;

import static org.assertj.core.api.Assertions.*;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CacheKeyBuilderTest {
  @Test
  void separatesNamespacesAndKeepsEnvironmentPrefix() {
    var keys = new CacheKeyBuilder("vishop:dev:");
    var tenant = UUID.fromString("00000000-0000-0000-0000-000000000001");
    assertThat(keys.build("subscription", "plan", 42)).isEqualTo("vishop:dev:subscription:plan:42");
    assertThat(keys.build("inventory", "product", tenant, 42))
        .isEqualTo("vishop:dev:inventory:product:" + tenant + ":42");
    assertThat(keys.withPrefix("auth:user:" + tenant)).isEqualTo("vishop:dev:auth:user:" + tenant);
  }

  @Test
  void emptyAndLiteralPrefixesKeepExistingAdapterContracts() {
    assertThat(new CacheKeyBuilder("").build("catalog", "product", 42))
        .isEqualTo("catalog:product:42");
    assertThat(new CacheKeyBuilder("literal-prefix").withPrefix("existing:key"))
        .isEqualTo("literal-prefixexisting:key");
  }

  @ParameterizedTest
  @ValueSource(strings = {"", " ", "catalog:product", " product", "product "})
  void rejectsAmbiguousOrBlankSegments(String segment) {
    var keys = new CacheKeyBuilder("app:");
    assertThatIllegalArgumentException().isThrownBy(() -> keys.build(segment, 42));
    assertThatIllegalArgumentException().isThrownBy(() -> keys.build("catalog", segment));
  }

  @Test
  void rejectsMissingIdsAndBlankLogicalKeys() {
    var keys = new CacheKeyBuilder("app:");
    assertThatIllegalArgumentException().isThrownBy(() -> keys.build("catalog", (Object) null));
    assertThatIllegalArgumentException().isThrownBy(() -> keys.withPrefix(null));
    assertThatIllegalArgumentException().isThrownBy(() -> keys.withPrefix(" "));
  }
}
