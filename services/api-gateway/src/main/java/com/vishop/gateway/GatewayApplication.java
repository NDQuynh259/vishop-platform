package com.vishop.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.actuate.autoconfigure.security.reactive.ReactiveManagementWebSecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.reactive.ReactiveSecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.reactive.ReactiveUserDetailsServiceAutoConfiguration;

@SpringBootApplication(
    exclude = {
      ReactiveSecurityAutoConfiguration.class,
      ReactiveUserDetailsServiceAutoConfiguration.class,
      ReactiveManagementWebSecurityAutoConfiguration.class
    })
public class GatewayApplication {

  // #region main
  public static void main(String[] args) {
    SpringApplication.run(GatewayApplication.class, args);
  }
  // #endregion
}
