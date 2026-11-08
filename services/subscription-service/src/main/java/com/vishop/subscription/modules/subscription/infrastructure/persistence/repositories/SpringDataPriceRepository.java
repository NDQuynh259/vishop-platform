package com.vishop.subscription.modules.subscription.infrastructure.persistence.repositories;

import com.vishop.subscription.modules.subscription.infrastructure.persistence.entities.PlanPriceJpaEntity;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;

public interface SpringDataPriceRepository extends JpaRepository<PlanPriceJpaEntity, UUID> {
  Page<PlanPriceJpaEntity> findByActiveTrueOrderByPlanCodeAscAmountVndAscIdAsc(Pageable pagination);

  long countByActiveTrue();
}
