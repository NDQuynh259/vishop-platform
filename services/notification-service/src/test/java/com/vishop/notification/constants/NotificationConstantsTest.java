package com.vishop.notification.constants;

import static org.assertj.core.api.Assertions.*;

import com.vishop.notification.shared.constants.NotificationFeatureCode;
import com.vishop.notification.shared.constants.NotificationTypeCode;
import com.vishop.notification.shared.contract.NotificationChannel;
import org.junit.jupiter.api.Test;

class NotificationConstantsTest {
  @Test
  void knownTypeDefinesFeatureCategoryAndImmutableChannelDefaults() {
    var type = NotificationTypeCode.fromCode("STOCK_LOW").orElseThrow();
    assertThat(type.feature()).isEqualTo(NotificationFeatureCode.INVENTORY);
    assertThat(type.category()).isEqualTo(NotificationTypeCode.Category.BUSINESS);
    assertThat(type.defaultEnabled()).isTrue();
    assertThat(type.defaultChannels()).containsExactly(NotificationChannel.IN_APP);
    assertThatThrownBy(() -> type.defaultChannels().clear())
        .isInstanceOf(UnsupportedOperationException.class);
    assertThat(NotificationTypeCode.SYSTEM_MAINTENANCE.category())
        .isEqualTo(NotificationTypeCode.Category.SYSTEM);
  }

  @Test
  void unknownLegacyAndNonCanonicalCodesAreNotImplicitlySupported() {
    assertThat(NotificationTypeCode.fromCode("CUSTOM_LEGACY_TYPE")).isEmpty();
    assertThat(NotificationTypeCode.fromCode("stock_low")).isEmpty();
    assertThat(NotificationTypeCode.fromCode(null)).isEmpty();
    assertThat(NotificationFeatureCode.fromCode("UNKNOWN_FEATURE")).isEmpty();
    assertThat(NotificationFeatureCode.fromCode(null)).isEmpty();
    assertThat(NotificationFeatureCode.fromCode("INVENTORY"))
        .contains(NotificationFeatureCode.INVENTORY);
  }
}
