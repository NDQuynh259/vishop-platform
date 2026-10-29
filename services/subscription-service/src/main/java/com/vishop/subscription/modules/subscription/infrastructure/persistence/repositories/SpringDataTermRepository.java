package com.vishop.subscription.modules.subscription.infrastructure.persistence.repositories;

import com.vishop.subscription.modules.subscription.infrastructure.persistence.entities.SubscriptionTermJpaEntity;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;

public interface SpringDataTermRepository extends JpaRepository<SubscriptionTermJpaEntity, UUID> {}
