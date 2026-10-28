package com.vishop.iam.infra.security;

import com.vishop.iam.shared.application.dto.AuthenticatedUser;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

public record IamPrincipal(AuthenticatedUser actor) implements UserDetails {

  public UUID tenantId() {
    return actor.tenantId();
  }

  public Set<String> permissions() {
    return actor.permissions();
  }

  public UUID userId() {
    return actor.userId();
  }

  public String systemRole() {
    return actor.systemRole();
  }

  public String tenantRole() {
    return actor.tenantRole();
  }

  @Override
  public Collection<? extends GrantedAuthority> getAuthorities() {
    var result = new ArrayList<GrantedAuthority>();
    permissions().forEach(permission -> result.add(new SimpleGrantedAuthority(permission)));
    return result;
  }

  @Override
  public String getPassword() {
    return "";
  }

  @Override
  public String getUsername() {
    return actor.userId().toString();
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
}
