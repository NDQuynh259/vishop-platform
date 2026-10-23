package com.vishop.iam.shared.infrastructure.security;

import com.vishop.iam.shared.application.dto.IamActor;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

public record IamPrincipal(IamActor actor) implements UserDetails {

  public UUID tenantId() {
    return actor.tenantId();
  }

  public Set<String> permissions() {
    return actor.permissions();
  }

  public UUID userId() {
    return actor.userId();
  }

  @Override
  public Collection<? extends GrantedAuthority> getAuthorities() {
    var result = new ArrayList<GrantedAuthority>();
    // System authorities have a separate prefix; a custom tenant role cannot impersonate one.
    result.add(new SimpleGrantedAuthority("SYSTEM_" + actor.systemRole()));
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
