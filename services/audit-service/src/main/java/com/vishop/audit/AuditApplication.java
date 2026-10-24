package com.vishop.audit;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = "com.vishop")
@EnableScheduling
public class AuditApplication {
  // #region main
  public static void main(String[] args) {
    SpringApplication.run(AuditApplication.class, args);
  }
  // #endregion
}
