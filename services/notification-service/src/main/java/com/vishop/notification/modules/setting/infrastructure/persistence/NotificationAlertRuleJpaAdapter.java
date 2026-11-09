package com.vishop.notification.modules.setting.infrastructure.persistence;

import com.vishop.notification.modules.setting.domain.entity.NotificationAlertRule;
import com.vishop.notification.modules.setting.domain.port.NotificationAlertRuleRepository;
import com.vishop.notification.modules.setting.infrastructure.persistence.entities.NotificationAlertRuleJpaEntity;
import com.vishop.notification.modules.setting.infrastructure.persistence.repositories.SpringDataNotificationAlertRuleRepository;
import com.vishop.notification.shared.contract.NotificationChannel;
import io.github.coreplatform.common.domain.UuidV7;
import io.github.coreplatform.common.domain.exception.ConflictException;
import io.github.coreplatform.common.domain.exception.EntityNotFoundException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.OptimisticLockException;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationAlertRuleJpaAdapter implements NotificationAlertRuleRepository {
  private final SpringDataNotificationAlertRuleRepository rows;
  private final EntityManager em;

  public List<NotificationAlertRule> findAll(UUID tenantId) {
    return rows.findByTenantIdAndDeletedAtIsNullOrderByType(tenantId).stream()
        .map(this::view)
        .toList();
  }

  public Optional<NotificationAlertRule> findByType(UUID tenantId, String type) {
    return rows.findByTenantIdAndTypeAndDeletedAtIsNull(tenantId, type).map(this::view);
  }

  public NotificationAlertRule findById(UUID tenantId, UUID ruleId) {
    return view(
        rows.findByIdAndTenantIdAndDeletedAtIsNull(ruleId, tenantId)
            .orElseThrow(() -> missing(ruleId)));
  }

  @Transactional
  public NotificationAlertRule create(UUID tenantId, NotificationAlertRule rule) {
    UUID id = UuidV7.nextId();
    if (rows.ensure(id, tenantId, rule.getType()) == 0)
      throw new ConflictException("Rule already exists for this type");
    var row = rows.lock(tenantId, id).orElseThrow(() -> missing(id));
    copy(row, rule);
    rows.flush();
    return view(row);
  }

  @Transactional
  public NotificationAlertRule update(
      UUID tenantId, NotificationAlertRule rule, long expectedVersion) {
    try {
      var row = rows.lock(tenantId, rule.getId()).orElseThrow(() -> missing(rule.getId()));
      em.refresh(row, LockModeType.PESSIMISTIC_WRITE);
      if (row.getDeletedAt() != null) throw missing(rule.getId());
      if (row.getVersion() != expectedVersion)
        throw new ConflictException("Rule version has changed");
      copy(row, rule);
      rows.flush();
      return view(row);
    } catch (OptimisticLockException | OptimisticLockingFailureException ex) {
      throw new ConflictException("Rule version has changed");
    }
  }

  private void copy(NotificationAlertRuleJpaEntity row, NotificationAlertRule rule) {
    if (!row.getType().equals(rule.getType()))
      throw new IllegalArgumentException("Rule type is immutable");
    row.setEnabled(rule.isEnabled());
    row.setChannels(rule.getChannels().stream().sorted().map(Enum::name).toArray(String[]::new));
    row.touch();
  }

  private NotificationAlertRule view(NotificationAlertRuleJpaEntity row) {
    var channels = EnumSet.noneOf(NotificationChannel.class);
    for (String channel : row.getChannels()) channels.add(NotificationChannel.valueOf(channel));
    return new NotificationAlertRule(
        row.getId(), row.getType(), row.isEnabled(), channels, row.getVersion());
  }

  private static EntityNotFoundException missing(UUID id) {
    return new EntityNotFoundException("Notification alert rule", id);
  }
}
