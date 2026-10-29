package com.vishop.subscription.modules.payment.infrastructure.persistence.repositories;

import com.vishop.subscription.modules.payment.infrastructure.persistence.entities.SePayInboxJpaEntity;
import jakarta.persistence.LockModeType;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface SpringDataInboxRepository extends JpaRepository<SePayInboxJpaEntity, Long> {
  org.springframework.data.domain.Page<SePayInboxJpaEntity> findByStatus(
      String status, Pageable page);

  @Modifying
  @Query(
      value =
          "INSERT INTO sepay_inbox(id,raw_payload,payload_hash,status,received_at) VALUES (:id,:raw,:hash,'PENDING',:now) ON CONFLICT DO NOTHING",
      nativeQuery = true)
  int receive(
      @Param("id") long id,
      @Param("raw") String raw,
      @Param("hash") String hash,
      @Param("now") java.time.Instant now);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select i from SePayInboxJpaEntity i where i.id=:id")
  Optional<SePayInboxJpaEntity> lock(@Param("id") long id);

  @Query(
      "select i.id from SePayInboxJpaEntity i where i.status='PENDING' order by i.receivedAt,i.id")
  List<Long> pending(Pageable page);
}
