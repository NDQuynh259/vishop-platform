package com.vishop.notification.modules.setting.infrastructure.persistence.adapters;

import com.vishop.notification.modules.setting.domain.entity.NotificationSettingState.Connection;
import com.vishop.notification.modules.setting.domain.port.NotificationSettingManagementRepository;
import com.vishop.notification.modules.setting.infrastructure.persistence.mapper.NotificationSettingPersistenceMapper;
import com.vishop.notification.modules.setting.infrastructure.persistence.repositories.SpringDataNotificationChannelSettingRepository;
import com.vishop.notification.shared.contract.NotificationChannel;
import io.github.coreplatform.common.domain.UuidV7;
import io.github.coreplatform.common.domain.exception.ConflictException;
import io.github.coreplatform.common.domain.exception.EntityNotFoundException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.OptimisticLockException;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional(readOnly = true)
public class NotificationSettingJpaAdapter implements NotificationSettingManagementRepository {
  private final SpringDataNotificationChannelSettingRepository channels;
  private final NotificationSettingPersistenceMapper mapper;
  private final EntityManager em;

  public NotificationSettingJpaAdapter(
      SpringDataNotificationChannelSettingRepository channels,
      NotificationSettingPersistenceMapper mapper,
      EntityManager em) {
    this.channels = channels;
    this.mapper = mapper;
    this.em = em;
  }

  @Transactional
  public Connection createConnection(UUID tenant, Connection connection) {
    UUID id = UuidV7.nextId();
    if (channels.ensure(id, tenant, connection.channel().name()) == 0)
      throw new ConflictException("Channel setting already exists");
    var entity = channels.lockById(tenant, id).orElseThrow(NotificationSettingJpaAdapter::conflict);
    mapper.copyConnection(entity, connection);
    channels.flush();
    return mapper.connection(entity.getChannel(), entity);
  }

  public Connection connection(UUID tenant, NotificationChannel channel) {
    var matches = channels.findAllByTenantIdAndChannelOrderByIdAsc(tenant, channel);
    if (matches.size() > 1)
      throw new ConflictException("Multiple channel connections; use settingId");
    return mapper.connection(channel, matches.isEmpty() ? null : matches.getFirst());
  }

  public List<Connection> connections(UUID tenant, NotificationChannel channel) {
    var matches =
        channels.findAllByTenantIdAndChannelAndDeletedAtIsNullOrderByIdAsc(tenant, channel);
    return matches.isEmpty()
        ? List.of(mapper.connection(channel, null))
        : matches.stream().map(entity -> mapper.connection(channel, entity)).toList();
  }

  public Connection connectionById(UUID tenant, UUID channelId) {
    var entity =
        channels
            .findByIdAndTenantIdAndDeletedAtIsNull(channelId, tenant)
            .orElseThrow(() -> new EntityNotFoundException("Notification setting", channelId));
    return mapper.connection(entity.getChannel(), entity);
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
      mapper.copyConnection(entity, connection);
      channels.flush();
      return mapper.connection(entity.getChannel(), entity);
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
      mapper.copyConnection(entity, connection);
      channels.flush();
      return mapper.connection(connection.channel(), entity);
    } catch (OptimisticLockException | OptimisticLockingFailureException ex) {
      throw conflict();
    }
  }

  private static ConflictException conflict() {
    return new ConflictException("Setting version has changed");
  }
}
