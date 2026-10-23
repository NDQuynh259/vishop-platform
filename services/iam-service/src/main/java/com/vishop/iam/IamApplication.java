package com.vishop.iam;

import io.github.coreplatform.common.security.config.CommonSecurityAutoConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Identity and tenant access share a database and transaction manager. */
@SpringBootApplication(exclude = CommonSecurityAutoConfiguration.class)
@org.springframework.scheduling.annotation.EnableScheduling
public class IamApplication {
  public static void main(String[] args) {
    SpringApplication.run(IamApplication.class, args);
  }
}
