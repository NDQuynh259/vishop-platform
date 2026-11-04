package com.vishop.notification.shared.constants;

import com.vishop.notification.shared.contract.NotificationChannel;
import java.util.Optional;
import java.util.Set;

/** Supported type codes and backend defaults. No catalog service, persistence or metadata IDs. */
public enum NotificationTypeCode {
  SYSTEM_ANNOUNCEMENT(NotificationFeatureCode.SYSTEM, Category.SYSTEM, "Thông báo từ hệ thống"),
  SYSTEM_MAINTENANCE(NotificationFeatureCode.SYSTEM, Category.SYSTEM, "Lịch bảo trì"),
  SYSTEM_INCIDENT(NotificationFeatureCode.SYSTEM, Category.SYSTEM, "Sự cố hệ thống"),
  MEMBER_INVITED(NotificationFeatureCode.IAM, Category.BUSINESS, "Lời mời thành viên"),
  SUBSCRIPTION_ACTIVATED(
      NotificationFeatureCode.SUBSCRIPTION, Category.BUSINESS, "Kích hoạt gói cước"),
  SUBSCRIPTION_EXPIRING(NotificationFeatureCode.SUBSCRIPTION, Category.BUSINESS, "Gói sắp hết hạn"),
  AUTHORIZATION_EXPIRING(
      NotificationFeatureCode.SHOP, Category.BUSINESS, "Ủy quyền gian hàng sắp hết hạn"),
  STOCK_PUSH_FAILED(NotificationFeatureCode.INVENTORY, Category.BUSINESS, "Đẩy tồn thất bại"),
  STOCK_LOW(NotificationFeatureCode.INVENTORY, Category.BUSINESS, "Tồn kho dưới ngưỡng"),
  PACKAGE_STOCK_SHORTAGE(
      NotificationFeatureCode.FULFILLMENT, Category.BUSINESS, "Kiện hàng thiếu tồn"),
  SHIPMENT_EXCEPTION(
      NotificationFeatureCode.FULFILLMENT, Category.BUSINESS, "Vận chuyển bất thường"),
  SKU_UNMAPPED(NotificationFeatureCode.PRODUCT, Category.BUSINESS, "SKU chưa ghép nối"),
  ORDER_LOSS(NotificationFeatureCode.FINANCE, Category.BUSINESS, "Đơn hàng lỗ"),
  RECONCILIATION_MISMATCH(NotificationFeatureCode.FINANCE, Category.BUSINESS, "Lệch đối soát"),
  SYNC_FAILED(NotificationFeatureCode.SYNC, Category.BUSINESS, "Đồng bộ lỗi");

  public enum Category {
    SYSTEM,
    BUSINESS
  }

  private final NotificationFeatureCode feature;
  private final Category category;
  private final String displayName;
  private final boolean defaultEnabled;
  private final Set<NotificationChannel> defaultChannels;

  NotificationTypeCode(NotificationFeatureCode feature, Category category, String displayName) {
    this.feature = feature;
    this.category = category;
    this.displayName = displayName;
    this.defaultEnabled = true;
    this.defaultChannels = Set.of(NotificationChannel.IN_APP);
  }

  public String code() {
    return name();
  }

  public NotificationFeatureCode feature() {
    return feature;
  }

  public Category category() {
    return category;
  }

  public String displayName() {
    return displayName;
  }

  public boolean defaultEnabled() {
    return defaultEnabled;
  }

  public Set<NotificationChannel> defaultChannels() {
    return defaultChannels;
  }

  public static Optional<NotificationTypeCode> fromCode(String code) {
    if (code == null) return Optional.empty();
    try {
      return Optional.of(valueOf(code));
    } catch (IllegalArgumentException invalid) {
      return Optional.empty();
    }
  }
}
