package com.vishop.notification.modules.setting.infrastructure.persistence;

import com.vishop.notification.modules.setting.domain.entity.NotificationSettingState.Connection;
import com.vishop.notification.modules.setting.domain.port.NotificationSettingManagementRepository;
import com.vishop.notification.modules.setting.infrastructure.persistence.mapper.NotificationSettingPersistenceMapper;
import com.vishop.notification.modules.setting.infrastructure.persistence.repositories.SpringDataNotificationChannelSettingRepository;
import com.vishop.notification.shared.contract.NotificationChannel;
import io.github.coreplatform.common.domain.UuidV7;
import io.github.coreplatform.common.domain.exception.ConflictException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.OptimisticLockException;
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

  public Connection connection(UUID tenant, NotificationChannel channel) {
    return mapper.connection(
        channel, channels.findByTenantIdAndChannel(tenant, channel).orElse(null));
  }

  @Transactional
  public Connection saveConnection(UUID tenant, Connection connection, long expected) {
    if (expected == 0) channels.ensure(UuidV7.nextId(), tenant, connection.channel().name());
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
