package com.vishop.tenant;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = "com.vishop")
@EnableScheduling
public class TenantApplication {

    // #region main
    public static void main(String[] args) {
        SpringApplication.run(TenantApplication.class, args);
    }
    // #endregion
}
