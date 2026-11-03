package io.github.coreplatform.common.security.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class AuthorizationSnapshotTest {
  private final ObjectMapper json = new ObjectMapper();

  @ParameterizedTest
  @EnumSource(AuthorizationStatus.class)
  void preservesIamStatusStringsInJson(AuthorizationStatus status) throws Exception {
    String payload =
        "{\"key\":\"auth:user:test\",\"version\":\"1\",\"status\":\"" + status.name() + "\"}";

    var snapshot = json.readValue(payload, AuthorizationSnapshot.class);

    assertThat(snapshot.status()).isEqualTo(status);
    assertThat(json.readTree(json.writeValueAsString(snapshot)).get("status").asText())
        .isEqualTo(status.name());
  }

  @Test
  void rejectsUnknownAuthorizationStatus() {
    assertThatThrownBy(
            () -> json.readValue("{\"status\":\"UNRECOGNIZED\"}", AuthorizationSnapshot.class))
        .isInstanceOf(InvalidFormatException.class);
  }
}
