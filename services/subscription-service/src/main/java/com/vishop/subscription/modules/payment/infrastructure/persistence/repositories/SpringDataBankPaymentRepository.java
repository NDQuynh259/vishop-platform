package com.vishop.subscription.modules.payment.infrastructure.persistence.repositories;

import com.vishop.subscription.modules.payment.infrastructure.persistence.entities.BankPaymentJpaEntity;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;

public interface SpringDataBankPaymentRepository extends JpaRepository<BankPaymentJpaEntity, Long> {
  Optional<BankPaymentJpaEntity> findByBankIgnoreCaseAndAccountNumberAndReferenceCode(
      String bank, String account, String reference);

  Page<BankPaymentJpaEntity> findByStatus(String status, Pageable page);
}
