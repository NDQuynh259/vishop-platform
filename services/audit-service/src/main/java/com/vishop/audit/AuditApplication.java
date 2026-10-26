package com.vishop.audit;

import com.vishop.audit.domain.AuditLogEntity;
import com.vishop.audit.domain.AuditLogRepository;
import io.github.coreplatform.common.events.consumer.KafkaConsumerConfig;
import io.github.coreplatform.common.persistence.idempotency.ConsumedMessageEntity;
import io.github.coreplatform.common.persistence.idempotency.ConsumedMessageRepository;
import io.github.coreplatform.common.persistence.idempotency.IdempotentConsumer;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

// Audit consumes events; it does not own an outbox or scan producer persistence components.
@SpringBootApplication(
    scanBasePackageClasses = {
      AuditApplication.class,
      KafkaConsumerConfig.class,
      IdempotentConsumer.class
    })
@EntityScan(basePackageClasses = {AuditLogEntity.class, ConsumedMessageEntity.class})
@EnableJpaRepositories(
    basePackageClasses = {AuditLogRepository.class, ConsumedMessageRepository.class})
@EnableScheduling
public class AuditApplication {
  // #region main
  public static void main(String[] args) {
    SpringApplication.run(AuditApplication.class, args);
  }
  // #endregion
}
