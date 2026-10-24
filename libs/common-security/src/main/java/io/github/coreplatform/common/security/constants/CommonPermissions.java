package io.github.coreplatform.common.security.constants;

/**
 * Standard permission constants for core platform services. Use
 * in @PreAuthorize("hasAuthority(CommonPermissions.Order.VIEW)") annotations.
 */
public final class CommonPermissions {

  // #region CommonPermissions
  private CommonPermissions() {}

  // #endregion

  public static final class Order {
    // #region Order
    private Order() {}

    // #endregion
    public static final String VIEW = "order.view";
    public static final String CREATE = "order.create";
    public static final String UPDATE = "order.update";
    public static final String CANCEL = "order.cancel";
    public static final String SHIP = "order.package.ship";
    public static final String PRINT = "order.print";
    public static final String SPLIT = "order.split";
    public static final String EXPORT = "order.export";
  }

  public static final class Product {
    // #region Product
    private Product() {}

    // #endregion
    public static final String VIEW = "product.view";
    public static final String CREATE = "product.create";
    public static final String UPDATE = "product.update";
    public static final String DELETE = "product.delete";
    public static final String VIEW_COST_PRICE = "product.view_cost_price";
    public static final String EDIT_COST_PRICE = "product.edit_cost_price";
    public static final String LINK_CHANNEL = "product.link_channel";
  }

  public static final class Inventory {
    // #region Inventory
    private Inventory() {}

    // #endregion
    public static final String VIEW = "inventory.view";
    public static final String ADJUST = "inventory.adjust";
    public static final String TRANSFER = "inventory.transfer";
    public static final String STOCK_TAKE = "inventory.stock_take";
  }

  public static final class Tenant {
    // #region Tenant
    private Tenant() {}

    // #endregion
    public static final String VIEW = "tenant.view";
    public static final String MANAGE = "tenant.manage";
    public static final String MEMBER_VIEW = "tenant.member.view";
    public static final String MEMBER_INVITE = "tenant.member.invite";
    public static final String MEMBER_ROLE_ASSIGN = "tenant.member.role_assign";
    public static final String MEMBER_REMOVE = "tenant.member.remove";
  }

  public static final class Shop {
    // #region Shop
    private Shop() {}

    // #endregion
    public static final String VIEW = "shop.view";
    public static final String CONNECT = "shop.connect";
    public static final String DISCONNECT = "shop.disconnect";
    public static final String SETTINGS = "shop.settings";
  }

  public static final class Finance {
    // #region Finance
    private Finance() {}

    // #endregion
    public static final String SETTLEMENT_VIEW = "finance.settlement.view";
    public static final String SETTLEMENT_EXPORT = "finance.settlement.export";
    public static final String RECONCILIATION = "finance.reconciliation";
  }

  public static final class Audit {
    // #region Audit
    private Audit() {}

    // #endregion
    public static final String VIEW = "audit_log.view";
    public static final String EXPORT = "audit_log.export";
  }
}
