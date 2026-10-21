package com.vishop.identity;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = "com.vishop")
@EnableScheduling
public class IdentityApplication {

    // #region main
    public static void main(String[] args) {
        SpringApplication.run(IdentityApplication.class, args);
    }
    // #endregion
}
