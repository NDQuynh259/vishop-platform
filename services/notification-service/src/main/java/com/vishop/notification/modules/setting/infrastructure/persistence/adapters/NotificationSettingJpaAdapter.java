package com.vishop.notification.modules.setting.infrastructure.persistence.adapters;

import com.vishop.notification.modules.setting.domain.entity.ChannelRuleSelection;
import com.vishop.notification.modules.setting.domain.entity.NotificationSettingState.Connection;
import com.vishop.notification.modules.setting.domain.port.NotificationSettingManagementRepository;
import com.vishop.notification.modules.setting.infrastructure.persistence.entities.NotificationAlertRuleJpaEntity;
import com.vishop.notification.modules.setting.infrastructure.persistence.entities.NotificationChannelSettingJpaEntity;
import com.vishop.notification.modules.setting.infrastructure.persistence.entities.NotificationSettingTypeJpaEntity;
import com.vishop.notification.modules.setting.infrastructure.persistence.mapper.NotificationSettingPersistenceMapper;
import com.vishop.notification.modules.setting.infrastructure.persistence.repositories.SpringDataNotificationAlertRuleRepository;
import com.vishop.notification.modules.setting.infrastructure.persistence.repositories.SpringDataNotificationChannelSettingRepository;
import com.vishop.notification.modules.setting.infrastructure.persistence.repositories.SpringDataNotificationSettingTypeRepository;
import com.vishop.notification.shared.constants.NotificationTypeCode;
import com.vishop.notification.shared.contract.NotificationChannel;
import io.github.coreplatform.common.domain.UuidV7;
import io.github.coreplatform.common.domain.exception.BusinessRuleViolationException;
import io.github.coreplatform.common.domain.exception.ConflictException;
import io.github.coreplatform.common.domain.exception.EntityNotFoundException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.OptimisticLockException;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationSettingJpaAdapter implements NotificationSettingManagementRepository {
  private final SpringDataNotificationChannelSettingRepository channels;
  private final NotificationSettingPersistenceMapper mapper;
  private final EntityManager em;
  private final SpringDataNotificationAlertRuleRepository rules;

  private final SpringDataNotificationSettingTypeRepository links;

  @Transactional
  public Connection createConnection(UUID tenant, Connection connection) {
    UUID id = UuidV7.nextId();
    if (channels.ensure(id, tenant, connection.channel().name()) == 0)
      throw new ConflictException("Channel setting already exists");
    var entity = channels.lockById(tenant, id).orElseThrow(NotificationSettingJpaAdapter::conflict);
    return save(entity, connection);
  }

  public Connection connection(UUID tenant, NotificationChannel channel) {
    var matches = channels.findAllByTenantIdAndChannelOrderByIdAsc(tenant, channel);
    if (matches.size() > 1)
      throw new ConflictException("Multiple channel connections; use settingId");
    return view(channel, matches.isEmpty() ? null : matches.getFirst());
  }

  public List<Connection> connections(UUID tenant, NotificationChannel channel) {
    var matches =
        channels.findAllByTenantIdAndChannelAndDeletedAtIsNullOrderByIdAsc(tenant, channel);
    if (matches.isEmpty()) return List.of(view(channel, null));
    var selected =
        channel.isGroupChannel()
            ? types(
                tenant, matches.stream().map(NotificationChannelSettingJpaEntity::getId).toList())
            : Map.<UUID, Set<NotificationTypeCode>>of();
    return matches.stream()
        .map(
            entity ->
                mapper.connection(channel, entity, selected.getOrDefault(entity.getId(), Set.of())))
        .toList();
  }

  public Connection connectionById(UUID tenant, UUID channelId) {
    var entity =
        channels
            .findByIdAndTenantIdAndDeletedAtIsNull(channelId, tenant)
            .orElseThrow(() -> new EntityNotFoundException("Notification setting", channelId));
    return view(entity.getChannel(), entity);
  }

  @Transactional
  public Connection saveConnectionById(
      UUID tenant, UUID channelId, Connection connection, long expected) {
    try {
      var entity =
          channels
              .lockById(tenant, channelId)
              .orElseThrow(() -> new EntityNotFoundException("Notification setting", channelId));
      em.refresh(entity, LockModeType.PESSIMISTIC_WRITE);
      if (entity.getDeletedAt() != null)
        throw new EntityNotFoundException("Notification setting", channelId);
      if (entity.getVersion() != expected) throw conflict();
      return save(entity, connection);
    } catch (OptimisticLockException | OptimisticLockingFailureException ex) {
      throw conflict();
    }
  }

  @Transactional
  public Connection saveConnection(UUID tenant, Connection connection, long expected) {
    var matches = channels.findAllByTenantIdAndChannelOrderByIdAsc(tenant, connection.channel());
    if (matches.size() > 1)
      throw new ConflictException("Multiple channel connections; use settingId");
    if (expected == 0 && matches.isEmpty())
      channels.ensure(UuidV7.nextId(), tenant, connection.channel().name());
    try {
      var entity =
          channels
              .lock(tenant, connection.channel())
              .orElseThrow(NotificationSettingJpaAdapter::conflict);
      em.refresh(entity, LockModeType.PESSIMISTIC_WRITE);
      if (entity.getVersion() != expected) throw conflict();
      return save(entity, connection);
    } catch (OptimisticLockException | OptimisticLockingFailureException ex) {
      throw conflict();
    }
  }

  private Map<UUID, Set<NotificationTypeCode>> types(UUID tenant, List<UUID> settingIds) {
    Map<UUID, Set<NotificationTypeCode>> result = new HashMap<>();
    for (var link : links.findSelections(tenant, settingIds)) {
      result
          .computeIfAbsent(
              link.getSetting().getId(), ignored -> EnumSet.noneOf(NotificationTypeCode.class))
          .add(NotificationTypeCode.valueOf(link.getRule().getType()));
    }
    return result;
  }

  private Connection view(NotificationChannel channel, NotificationChannelSettingJpaEntity entity) {
    var selected =
        entity != null && channel.isGroupChannel()
            ? types(entity.getTenantId(), List.of(entity.getId()))
                .getOrDefault(entity.getId(), Set.of())
            : Set.<NotificationTypeCode>of();
    return mapper.connection(channel, entity, selected);
  }

  private Connection save(NotificationChannelSettingJpaEntity entity, Connection connection) {
    if (entity.getChannel().isGroupChannel()) {
      var additions = EnumSet.noneOf(NotificationTypeCode.class);
      additions.addAll(connection.allowedTypes());
      for (var link : links.findSelections(entity.getTenantId(), List.of(entity.getId()))) {
        if (!additions.remove(NotificationTypeCode.valueOf(link.getRule().getType())))
          links.delete(link);
      }
      // EnumSet acquires rule rows in the same order across concurrent group updates.
      for (var type : additions) {
        rules.ensure(UuidV7.nextId(), entity.getTenantId(), type.code(), type.feature().name());
        var rule = rules.findByTenantIdAndType(entity.getTenantId(), type.code()).orElseThrow();
        links.save(new NotificationSettingTypeJpaEntity(UuidV7.nextId(), entity, rule));
      }
    }
    mapper.copyConnection(entity, connection);
    channels.flush();
    return mapper.connection(entity.getChannel(), entity, connection.allowedTypes());
  }

  @Override
  public ChannelRuleSelection channelRules(UUID tenant, UUID settingId) {
    var setting =
        channels
            .findByIdAndTenantIdAndDeletedAtIsNull(settingId, tenant)
            .orElseThrow(() -> new EntityNotFoundException("Notification setting", settingId));
    requireGroup(setting);
    var ids =
        links.findSelections(tenant, List.of(settingId)).stream()
            .map(link -> link.getRule().getId())
            .collect(java.util.stream.Collectors.toSet());
    return new ChannelRuleSelection(settingId, setting.getVersion(), ids);
  }

  @Override
  @Transactional
  public ChannelRuleSelection replaceChannelRules(
      UUID tenant, UUID settingId, long version, Set<UUID> ruleIds) {
    var setting =
        channels
            .lockById(tenant, settingId)
            .orElseThrow(() -> new EntityNotFoundException("Notification setting", settingId));
    requireGroup(setting);
    if (setting.getVersion() != version) throw conflict();
    var additions = new java.util.HashMap<UUID, NotificationAlertRuleJpaEntity>();
    if (!ruleIds.isEmpty()) {
      for (var rule : rules.lockActiveByIds(tenant, ruleIds)) additions.put(rule.getId(), rule);
      if (additions.size() != ruleIds.size())
        throw new EntityNotFoundException("Notification rule selection", settingId);
    }
    for (var link : links.findSelections(tenant, List.of(settingId))) {
      if (additions.remove(link.getRule().getId()) == null) links.delete(link);
    }
    for (var rule : additions.values())
      links.save(new NotificationSettingTypeJpaEntity(UuidV7.nextId(), setting, rule));
    setting.touch();
    channels.flush();
    return new ChannelRuleSelection(settingId, setting.getVersion(), ruleIds);
  }

  private static void requireGroup(NotificationChannelSettingJpaEntity setting) {
    if (!setting.getChannel().isGroupChannel())
      throw new BusinessRuleViolationException("Rule selection requires a Telegram or Lark group");
  }

  private static ConflictException conflict() {
    return new ConflictException("Setting version has changed");
  }
}
