package io.github.coreplatform.common.security.context;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import java.io.Serializable;
import java.util.Collection;
import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * In-memory Spring Security UserDetails representation for microservices.
 * Reconstructed from trusted API Gateway internal headers in 0ms without hitting the users database.
 */
public record CommonUserDetails(
        UUID userId,
        UUID tenantId,
        Set<UUID> accessibleShopIds,
        Collection<? extends GrantedAuthority> authorities
) implements UserDetails, Serializable {

    public CommonUserDetails {
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        accessibleShopIds = accessibleShopIds != null ? Set.copyOf(accessibleShopIds) : Collections.emptySet();
        authorities = authorities != null ? Collections.unmodifiableCollection(authorities) : Collections.emptyList();
    }

    @Override
    public String getUsername() {
        return userId.toString();
    }

    @Override
    public String getPassword() {
        return "";
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    /**
     * Checks if the current user has access rights to the given shop within the tenant.
     */
    public boolean hasShopAccess(UUID shopId) {
        if (shopId == null) {
            return false;
        }
        return accessibleShopIds.contains(shopId);
    }

    /**
     * Checks if the current user holds the specified authority or permission.
     */
    public boolean hasAuthority(String authority) {
        if (authority == null || authority.isBlank()) {
            return false;
        }
        return authorities.stream()
                .anyMatch(a -> a.getAuthority().equalsIgnoreCase(authority.trim()));
    }
}
