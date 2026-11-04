package com.vishop.notification.shared.constants;

import java.util.Optional;

/** Stable feature codes stored in tenant configuration; labels are maintained in code. */
public enum NotificationFeatureCode {
  SYSTEM("Hệ thống"),
  IAM("Tài khoản và thành viên"),
  SUBSCRIPTION("Gói cước"),
  SHOP("Kết nối gian hàng"),
  INVENTORY("Kho hàng"),
  PRODUCT("Sản phẩm"),
  FULFILLMENT("Xử lý và vận chuyển đơn"),
  FINANCE("Tài chính và đối soát"),
  SYNC("Đồng bộ dữ liệu"),
  LEGACY("Loại thông báo hiện có khác");
  private final String displayName;

  NotificationFeatureCode(String displayName) {
    this.displayName = displayName;
  }

  public String code() {
    return name();
  }

  public String displayName() {
    return displayName;
  }

  public static Optional<NotificationFeatureCode> fromCode(String code) {
    if (code == null) return Optional.empty();
    try {
      return Optional.of(valueOf(code));
    } catch (IllegalArgumentException invalid) {
      return Optional.empty();
    }
  }
}
