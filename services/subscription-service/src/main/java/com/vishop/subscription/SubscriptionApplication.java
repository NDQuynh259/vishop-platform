package com.vishop.subscription;

import com.vishop.subscription.modules.payment.infrastructure.persistence.entities.BankPaymentJpaEntity;
import com.vishop.subscription.modules.payment.infrastructure.persistence.entities.SePayInboxJpaEntity;
import com.vishop.subscription.modules.subscription.infrastructure.persistence.entities.PlanPriceJpaEntity;
import com.vishop.subscription.modules.subscription.infrastructure.persistence.entities.PurchaseOrderJpaEntity;
import com.vishop.subscription.modules.subscription.infrastructure.persistence.entities.SubscriptionAccountJpaEntity;
import com.vishop.subscription.modules.subscription.infrastructure.persistence.entities.SubscriptionTermJpaEntity;
import io.github.coreplatform.common.persistence.OutboxEventEntity;
import io.github.coreplatform.common.persistence.OutboxEventRepository;
import io.github.coreplatform.common.persistence.idempotency.ConsumedMessageRepository;
import io.github.coreplatform.common.security.config.CommonSecurityAutoConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.persistenceunit.PersistenceManagedTypes;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(exclude = CommonSecurityAutoConfiguration.class)
@EnableJpaRepositories(
    basePackageClasses = {SubscriptionApplication.class, OutboxEventRepository.class},
    excludeFilters =
        @ComponentScan.Filter(
            type = FilterType.ASSIGNABLE_TYPE,
            classes = ConsumedMessageRepository.class))
@EnableScheduling
public class SubscriptionApplication {
  // Billing is an outbox producer; it does not consume Kafka or own consumed_messages.
  @Bean
  PersistenceManagedTypes subscriptionManagedTypes() {
    return PersistenceManagedTypes.of(
        PlanPriceJpaEntity.class.getName(),
        SubscriptionAccountJpaEntity.class.getName(),
        PurchaseOrderJpaEntity.class.getName(),
        SubscriptionTermJpaEntity.class.getName(),
        SePayInboxJpaEntity.class.getName(),
        BankPaymentJpaEntity.class.getName(),
        OutboxEventEntity.class.getName());
  }

  public static void main(String[] args) {
    SpringApplication.run(SubscriptionApplication.class, args);
  }
}
