package com.vishop.audit;

import com.vishop.audit.modules.auditlog.infrastructure.persistence.entities.AuditLogJpaEntity;
import com.vishop.audit.modules.auditlog.infrastructure.persistence.repositories.SpringDataAuditLogRepository;
import io.github.coreplatform.common.events.consumer.KafkaConsumerConfig;
import io.github.coreplatform.common.persistence.idempotency.ConsumedMessageEntity;
import io.github.coreplatform.common.persistence.idempotency.ConsumedMessageRepository;
import io.github.coreplatform.common.persistence.idempotency.IdempotentConsumer;
import io.github.coreplatform.common.security.config.CommonSecurityAutoConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

// Audit consumes events; it does not own an outbox or scan producer persistence components.
@SpringBootApplication(
    exclude = CommonSecurityAutoConfiguration.class,
    scanBasePackageClasses = {
      AuditApplication.class,
      KafkaConsumerConfig.class,
      IdempotentConsumer.class
    })
@EntityScan(basePackageClasses = {AuditLogJpaEntity.class, ConsumedMessageEntity.class})
@EnableJpaRepositories(
    basePackageClasses = {SpringDataAuditLogRepository.class, ConsumedMessageRepository.class})
public class AuditApplication {
  // #region main
  public static void main(String[] args) {
    SpringApplication.run(AuditApplication.class, args);
  }
  // #endregion
}
