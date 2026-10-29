package com.vishop.subscription.modules.payment.infrastructure.persistence.repositories;

import com.vishop.subscription.modules.payment.infrastructure.persistence.entities.PaymentTransactionJpaEntity;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;

public interface SpringDataPaymentTransactionRepository
    extends JpaRepository<PaymentTransactionJpaEntity, UUID> {
  Optional<PaymentTransactionJpaEntity> findByBankIgnoreCaseAndAccountNumberAndReferenceCode(
      String bank, String account, String reference);

  Page<PaymentTransactionJpaEntity> findByStatus(String status, Pageable page);
}
