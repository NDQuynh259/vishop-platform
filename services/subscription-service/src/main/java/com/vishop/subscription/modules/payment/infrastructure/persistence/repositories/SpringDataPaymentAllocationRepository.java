package com.vishop.subscription.modules.payment.infrastructure.persistence.repositories;

import com.vishop.subscription.modules.payment.infrastructure.persistence.entities.PaymentAllocationJpaEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SpringDataPaymentAllocationRepository
    extends JpaRepository<PaymentAllocationJpaEntity, UUID> {}
