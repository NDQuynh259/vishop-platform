package io.github.coreplatform.common.security.context;

import java.io.Serializable;
import java.util.Collection;
import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * In-memory Spring Security UserDetails representation for microservices. Reconstructed from a
 * verified IAM access token. Tenant role names and system authorities use separate namespaces;
 * request headers alone confer no access.
 */
public record CommonUserDetails(
    UUID userId,
    UUID tenantId,
    Set<UUID> accessibleShopIds,
    Collection<? extends GrantedAuthority> authorities)
    implements UserDetails, Serializable {

  // #region CommonUserDetails
  public CommonUserDetails {
    Objects.requireNonNull(userId, "userId must not be null");
    Objects.requireNonNull(tenantId, "tenantId must not be null");
    accessibleShopIds =
        accessibleShopIds != null ? Set.copyOf(accessibleShopIds) : Collections.emptySet();
    authorities =
        authorities != null
            ? Collections.unmodifiableCollection(authorities)
            : Collections.emptyList();
  }

  // #endregion

  // #region getUsername
  @Override
  public String getUsername() {
    return userId.toString();
  }

  // #endregion

  // #region getPassword
  @Override
  public String getPassword() {
    return "";
  }

  // #endregion

  // #region getAuthorities
  @Override
  public Collection<? extends GrantedAuthority> getAuthorities() {
    return authorities;
  }

  // #endregion

  // #region isAccountNonExpired
  @Override
  public boolean isAccountNonExpired() {
    return true;
  }

  // #endregion

  // #region isAccountNonLocked
  @Override
  public boolean isAccountNonLocked() {
    return true;
  }

  // #endregion

  // #region isCredentialsNonExpired
  @Override
  public boolean isCredentialsNonExpired() {
    return true;
  }

  // #endregion

  // #region isEnabled
  @Override
  public boolean isEnabled() {
    return true;
  }

  // #endregion

  // #region hasShopAccess
  /** Checks if the current user has access rights to the given shop within the tenant. */
  public boolean hasShopAccess(UUID shopId) {
    if (shopId == null) {
      return false;
    }
    return accessibleShopIds.contains(shopId);
  }

  // #endregion

  // #region hasAuthority
  /** Checks if the current user holds the specified authority or permission. */
  public boolean hasAuthority(String authority) {
    if (authority == null || authority.isBlank()) {
      return false;
    }
    return authorities.stream().anyMatch(a -> a.getAuthority().equalsIgnoreCase(authority.trim()));
  }
  // #endregion
}
