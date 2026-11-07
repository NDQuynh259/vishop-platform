package io.github.coreplatform.common.security.annotation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.coreplatform.common.security.config.PermissionSecurityAutoConfiguration;
import io.github.coreplatform.common.security.constants.CommonPermissions.Notification;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

class RequirePermissionTest {
  AnnotationConfigApplicationContext context;
  SettingsService service;

  @Configuration(proxyBeanMethods = false)
  @EnableMethodSecurity
  static class Config {
    @Bean
    SettingsService settingsService() {
      return new SettingsService();
    }
  }

  @RequirePermission(Notification.SETTINGS_VIEW)
  static class SettingsService {
    public String view() {
      return "view";
    }

    @RequirePermission(Notification.SETTINGS_MANAGE)
    public String manage() {
      return "manage";
    }

    @PreAuthorize("isAuthenticated()")
    public String authenticated() {
      return "authenticated";
    }
  }

  @BeforeEach
  void setup() {
    context =
        new AnnotationConfigApplicationContext(
            PermissionSecurityAutoConfiguration.class, Config.class);
    service = context.getBean(SettingsService.class);
  }

  @AfterEach
  void cleanup() {
    SecurityContextHolder.clearContext();
    context.close();
  }

  void authority(String permission) {
    SecurityContextHolder.getContext()
        .setAuthentication(
            new UsernamePasswordAuthenticationToken(
                "user", "unused", List.of(new SimpleGrantedAuthority(permission))));
  }

  @Test
  void matchingPermissionAllowsClassLevelAnnotation() {
    authority(Notification.SETTINGS_VIEW);
    assertThat(service.view()).isEqualTo("view");
  }

  @Test
  void methodPermissionOverridesClassPermission() {
    authority(Notification.SETTINGS_MANAGE);
    assertThat(service.manage()).isEqualTo("manage");
    assertThatThrownBy(service::view).isInstanceOf(AccessDeniedException.class);
  }

  @Test
  void viewPermissionDoesNotAllowManage() {
    authority(Notification.SETTINGS_VIEW);
    assertThatThrownBy(service::manage).isInstanceOf(AccessDeniedException.class);
  }

  @Test
  void missingAuthenticationIsDenied() {
    assertThatThrownBy(service::manage)
        .isInstanceOf(AuthenticationCredentialsNotFoundException.class);
  }

  @Test
  void regularPreAuthorizeStillWorks() {
    authority("unrelated.permission");
    assertThat(service.authenticated()).isEqualTo("authenticated");
  }
}
