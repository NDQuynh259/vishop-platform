package com.vishop.iam.modules.identity.infrastructure.persistence.adapters;

import com.vishop.iam.modules.identity.application.dto.UserView;
import com.vishop.iam.modules.identity.application.mapper.UserViewMapper;
import com.vishop.iam.modules.identity.application.port.UserReadPort;
import com.vishop.iam.modules.identity.infrastructure.persistence.entities.UserJpaEntity;
import jakarta.persistence.EntityManager;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
public class UserReadAdapter implements UserReadPort {
  private final EntityManager entityManager;

  public UserReadAdapter(EntityManager entityManager) {
    this.entityManager = entityManager;
  }

  public List<UserView> users(int limit, int offset) {
    return entityManager
        .createQuery(
            "select u from UserJpaEntity u order by u.createdAt desc,u.id desc",
            UserJpaEntity.class)
        .setFirstResult(offset)
        .setMaxResults(limit)
        .getResultList()
        .stream()
        .map(UserJpaEntity::toDomain)
        .map(UserViewMapper::from)
        .toList();
  }
}
