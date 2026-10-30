package com.vishop.iam;

import io.github.coreplatform.common.persistence.OutboxEventEntity;
import io.github.coreplatform.common.persistence.OutboxEventRepository;
import io.github.coreplatform.common.security.config.CommonSecurityAutoConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/** Identity and tenant access share a database and transaction manager. */
@SpringBootApplication(
    exclude = {
      CommonSecurityAutoConfiguration.class,
      SecurityAutoConfiguration.class,
      org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration
          .class,
      org.springframework.boot.actuate.autoconfigure.security.servlet
          .ManagementWebSecurityAutoConfiguration.class
    })
@EntityScan(basePackageClasses = {IamApplication.class, OutboxEventEntity.class})
@EnableJpaRepositories(basePackageClasses = {IamApplication.class, OutboxEventRepository.class})
@org.springframework.scheduling.annotation.EnableScheduling
public class IamApplication {
  public static void main(String[] args) {
    SpringApplication.run(IamApplication.class, args);
  }
}
