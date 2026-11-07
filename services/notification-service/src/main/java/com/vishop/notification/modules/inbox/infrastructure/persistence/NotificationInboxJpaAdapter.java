package com.vishop.notification.modules.inbox.infrastructure.persistence;

import com.vishop.notification.modules.inbox.domain.entity.Notification;
import com.vishop.notification.modules.inbox.domain.entity.NotificationInboxEntry;
import com.vishop.notification.modules.inbox.domain.entity.NotificationInboxFilter;
import com.vishop.notification.modules.inbox.domain.entity.NotificationInboxScope;
import com.vishop.notification.modules.inbox.domain.entity.NotificationRecipient;
import com.vishop.notification.modules.inbox.domain.port.NotificationInboxRepository;
import com.vishop.notification.modules.inbox.infrastructure.persistence.entities.NotificationJpaEntity;
import com.vishop.notification.shared.contract.NotificationMessage;
import io.github.coreplatform.common.domain.exception.ConflictException;
import io.github.coreplatform.common.domain.exception.EntityNotFoundException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationInboxJpaAdapter implements NotificationInboxRepository {
  private final EntityManager em;

  @Transactional
  public UUID append(Notification notification, List<NotificationRecipient> recipients) {
    var m = notification.message();
    for (var recipient : recipients) {
      if (!m.tenantId().equals(recipient.tenantId())
          || !notification.id().equals(recipient.notificationId()))
        throw new IllegalArgumentException("Recipient does not belong to the notification");
    }
    int inserted =
        em.createNativeQuery(
                "INSERT INTO"
                    + " notifications(id,tenant_id,source_event_id,type,subject_type,subject_id,shop_id,required_permission,severity,title,body,action_url,occurred_at,created_at,updated_at)"
                    + " VALUES(:id,:tenant,:event,:type,:subjectType,:subjectId,:shop,:permission,:severity,:title,:body,:action,:occurred,:created,:created)"
                    + " ON CONFLICT(tenant_id,source_event_id,type,subject_type,subject_id) DO"
                    + " NOTHING")
            .setParameter("id", notification.id())
            .setParameter("tenant", m.tenantId())
            .setParameter("event", m.sourceEventId())
            .setParameter("type", m.type())
            .setParameter("subjectType", m.subjectType())
            .setParameter("subjectId", m.subjectId())
            .setParameter("shop", m.shopId())
            .setParameter("permission", m.requiredPermission())
            .setParameter("severity", m.severity().name())
            .setParameter("title", m.title())
            .setParameter("body", m.body())
            .setParameter("action", m.actionUrl())
            .setParameter("occurred", m.occurredAt())
            .setParameter("created", notification.createdAt())
            .executeUpdate();
    if (inserted == 0) {
      var existing =
          em.createQuery(
                  "select n from NotificationJpaEntity n where n.tenantId=:tenant and"
                      + " n.sourceEventId=:event and n.type=:type and n.subjectType=:subjectType"
                      + " and n.subjectId=:subjectId",
                  NotificationJpaEntity.class)
              .setParameter("tenant", m.tenantId())
              .setParameter("event", m.sourceEventId())
              .setParameter("type", m.type())
              .setParameter("subjectType", m.subjectType())
              .setParameter("subjectId", m.subjectId())
              .getSingleResult();
      if (!message(existing).equals(m))
        throw new ConflictException(
            "Source event already exists with a different notification payload");
      return existing.getId();
    }
    for (var r : recipients) {
      em.createNativeQuery(
              "INSERT INTO"
                  + " notification_recipients(tenant_id,notification_id,user_id,created_at,updated_at)"
                  + " VALUES(:tenant,:id,:user,:created,:created)")
          .setParameter("tenant", r.tenantId())
          .setParameter("id", r.notificationId())
          .setParameter("user", r.userId())
          .setParameter("created", r.createdAt())
          .executeUpdate();
    }
    return notification.id();
  }

  public List<NotificationInboxEntry> findAll(
      NotificationInboxScope scope, NotificationInboxFilter filter) {
    if (scope.getPermissions().isEmpty()) return List.of();
    String conditions = visible(scope) + " and r.deletedAt is null";
    if (filter.getType() != null) conditions += " and n.type=:type";
    if (filter.getSeverity() != null) conditions += " and n.severity=:severity";
    if (filter.getRead() != null)
      conditions += filter.getRead() ? " and r.readAt is not null" : " and r.readAt is null";
    if (filter.getShopId() != null) conditions += " and n.shopId=:shopId";
    if (filter.getBeforeCreatedAt() != null)
      conditions += " and (n.createdAt<:before or (n.createdAt=:before and n.id<:beforeId))";
    var query =
        em.createQuery(
            "select n,r.readAt from NotificationJpaEntity n,NotificationRecipientJpaEntity r where "
                + conditions
                + " order by n.createdAt desc,n.id desc",
            Object[].class);
    bind(query, scope);
    if (filter.getType() != null) query.setParameter("type", filter.getType());
    if (filter.getSeverity() != null) query.setParameter("severity", filter.getSeverity());
    if (filter.getShopId() != null) query.setParameter("shopId", filter.getShopId());
    if (filter.getBeforeCreatedAt() != null)
      query
          .setParameter("before", filter.getBeforeCreatedAt())
          .setParameter("beforeId", filter.getBeforeId());
    return query.setMaxResults(filter.getLimit()).getResultList().stream()
        .map(this::entry)
        .toList();
  }

  public Optional<NotificationInboxEntry> findById(NotificationInboxScope scope, UUID id) {
    if (scope.getPermissions().isEmpty()) return Optional.empty();
    var query =
        em.createQuery(
            "select n,r.readAt from NotificationJpaEntity n,NotificationRecipientJpaEntity r where "
                + visible(scope)
                + " and r.deletedAt is null and n.id=:id",
            Object[].class);
    bind(query, scope).setParameter("id", id);
    return query.getResultList().stream().findFirst().map(this::entry);
  }

  public long unreadCount(NotificationInboxScope scope) {
    if (scope.getPermissions().isEmpty()) return 0;
    var query =
        em.createQuery(
            "select count(n.id) from NotificationJpaEntity n,NotificationRecipientJpaEntity r where"
                + " "
                + visible(scope)
                + " and r.deletedAt is null and r.readAt is null",
            Long.class);
    bind(query, scope);
    return query.getSingleResult();
  }

  @Transactional
  public NotificationInboxEntry markRead(NotificationInboxScope scope, UUID id, Instant at) {
    if (!scope.getPermissions().isEmpty()) {
      var query =
          em.createQuery(
              "update NotificationRecipientJpaEntity r set r.readAt=case when r.createdAt>:at then"
                  + " r.createdAt else :at end,r.updatedAt=case when r.updatedAt>:at then"
                  + " r.updatedAt else :at end,r.version=r.version+1 where "
                  + recipientScope(scope)
                  + " and r.deletedAt is null and r.readAt is null and r.notificationId=:id");
      bind(query, scope).setParameter("id", id).setParameter("at", at).executeUpdate();
    }
    return findById(scope, id).orElseThrow(() -> missing(id));
  }

  @Transactional
  public int markAllRead(NotificationInboxScope scope, Instant before, Instant at) {
    if (scope.getPermissions().isEmpty()) return 0;
    var query =
        em.createQuery(
            "update NotificationRecipientJpaEntity r set r.readAt=case when r.createdAt>:at then"
                + " r.createdAt else :at end,r.updatedAt=case when r.updatedAt>:at then r.updatedAt"
                + " else :at end,r.version=r.version+1 where "
                + recipientScope(scope)
                + " and r.deletedAt is null and r.readAt is null and r.createdAt<=:before");
    return bind(query, scope).setParameter("at", at).setParameter("before", before).executeUpdate();
  }

  @Transactional
  public void archive(NotificationInboxScope scope, UUID id, Instant at) {
    if (scope.getPermissions().isEmpty()) throw missing(id);
    var existing =
        em.createQuery(
            "select count(n.id) from NotificationJpaEntity n,NotificationRecipientJpaEntity r where"
                + " "
                + visible(scope)
                + " and n.id=:id",
            Long.class);
    bind(existing, scope).setParameter("id", id);
    if (existing.getSingleResult() == 0) throw missing(id);
    var query =
        em.createQuery(
            "update NotificationRecipientJpaEntity r set r.deletedAt=case when r.createdAt>:at then"
                + " r.createdAt else :at end,r.updatedAt=case when r.updatedAt>:at then r.updatedAt"
                + " else :at end,r.version=r.version+1 where "
                + recipientScope(scope)
                + " and r.deletedAt is null and r.notificationId=:id");
    bind(query, scope).setParameter("id", id).setParameter("at", at).executeUpdate();
  }

  private String visible(NotificationInboxScope scope) {
    return "n.id=r.notificationId and n.tenantId=r.tenantId and r.tenantId=:tenant and"
               + " r.userId=:user and "
        + payloadScope(scope);
  }

  private String recipientScope(NotificationInboxScope scope) {
    return "r.tenantId=:tenant and r.userId=:user and r.notificationId in (select n.id from"
               + " NotificationJpaEntity n where "
        + payloadScope(scope)
        + ")";
  }

  private String payloadScope(NotificationInboxScope scope) {
    return "n.tenantId=:tenant and n.deletedAt is null and n.requiredPermission in :permissions and"
               + " "
        + (scope.getShopIds().isEmpty()
            ? "n.shopId is null"
            : "(n.shopId is null or n.shopId in :shops)");
  }

  private Query bind(Query query, NotificationInboxScope scope) {
    query
        .setParameter("tenant", scope.getTenantId())
        .setParameter("user", scope.getUserId())
        .setParameter("permissions", scope.getPermissions());
    if (!scope.getShopIds().isEmpty()) query.setParameter("shops", scope.getShopIds());
    return query;
  }

  private NotificationInboxEntry entry(Object[] row) {
    var n = (NotificationJpaEntity) row[0];
    return new NotificationInboxEntry(
        new Notification(n.getId(), message(n), n.getCreatedAt()), (Instant) row[1]);
  }

  private NotificationMessage message(NotificationJpaEntity n) {
    return new NotificationMessage(
        n.getTenantId(),
        n.getSourceEventId(),
        n.getType(),
        n.getSubjectType(),
        n.getSubjectId(),
        n.getRequiredPermission(),
        n.getShopId(),
        n.getSeverity(),
        n.getTitle(),
        n.getBody(),
        n.getActionUrl(),
        n.getOccurredAt());
  }

  private static EntityNotFoundException missing(UUID id) {
    return new EntityNotFoundException("Notification", id);
  }
}
