package io.github.coreplatform.common.domain.constants;

/** Shared system-wide roles. Tenant roles never grant these privileges. */
public enum SystemRole {
  USER,
  PLATFORM_ADMIN;

  public static boolean isValid(String value) {
    for (SystemRole role : values()) {
      if (role.name().equals(value)) return true;
    }
    return false;
  }
}
