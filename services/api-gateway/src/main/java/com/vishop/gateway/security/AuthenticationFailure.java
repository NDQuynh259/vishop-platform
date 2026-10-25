package com.vishop.gateway.security;

public class AuthenticationFailure extends RuntimeException {
  private final int status;
  private final String code;

  public AuthenticationFailure(int status, String code) {
    super(code);
    this.status = status;
    this.code = code;
  }

  public int status() {
    return status;
  }

  public String code() {
    return code;
  }
}
