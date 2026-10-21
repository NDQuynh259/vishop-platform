package io.github.coreplatform.common.security.authorization;

import io.github.coreplatform.common.security.context.SecurityContextHelper;
import org.springframework.stereotype.Component;
import java.util.UUID;

/**
 * SpEL bean helper for method security expressions.
 * Example usage: @PreAuthorize("hasAuthority('product.update') and @shopSecurity.hasAccess(#shopId)")
 */
@Component("shopSecurity")
public class ShopSecurity {

    /**
     * Checks if the currently authenticated user has access to the specified shop.
     */
    public boolean hasAccess(UUID shopId) {
        if (shopId == null) {
            return false;
        }
        return SecurityContextHelper.getCurrentUser()
                .map(user -> user.hasShopAccess(shopId))
                .orElse(false);
    }
}
