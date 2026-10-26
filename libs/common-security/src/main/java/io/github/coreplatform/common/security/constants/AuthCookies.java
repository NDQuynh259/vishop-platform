package io.github.coreplatform.common.security.constants;

/** Browser authentication cookies and the required non-simple header for state-changing APIs. */
public final class AuthCookies {
  public static final String ACCESS_TOKEN = "vishop_access_token";
  public static final String REFRESH_TOKEN = "vishop_refresh_token";
  public static final String ACCESS_PATH = "/";
  public static final String REFRESH_PATH = "/api/v1/auth";
  public static final String REQUEST_HEADER = "X-Requested-With";
  public static final String REQUEST_HEADER_VALUE = "XMLHttpRequest";

  private AuthCookies() {}

  public static boolean requiresRequestHeader(String method) {
    return !java.util.Set.of("GET", "HEAD", "OPTIONS").contains(method);
  }
}
