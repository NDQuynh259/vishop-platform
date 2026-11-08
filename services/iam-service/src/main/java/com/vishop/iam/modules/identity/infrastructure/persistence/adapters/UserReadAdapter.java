package com.vishop.iam.modules.identity.infrastructure.persistence.adapters;

import com.vishop.iam.modules.identity.application.dto.UserView;
import com.vishop.iam.modules.identity.application.mapper.UserViewMapper;
import com.vishop.iam.modules.identity.application.port.UserReadPort;
import com.vishop.iam.modules.identity.infrastructure.persistence.entities.UserJpaEntity;
import io.github.coreplatform.common.domain.pagination.ListPagination;
import io.github.coreplatform.common.domain.pagination.PagedResult;
import io.github.coreplatform.common.persistence.pagination.JpaListPagination;
import jakarta.persistence.EntityManager;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

@Repository
public class UserReadAdapter implements UserReadPort {
  private final EntityManager entityManager;

  public UserReadAdapter(EntityManager entityManager) {
    this.entityManager = entityManager;
  }

  public PagedResult<UserView> users(ListPagination pagination) {
    return JpaListPagination.find(
        entityManager,
        UserJpaEntity.class,
        (root, query, cb) -> cb.conjunction(),
        Sort.by(Sort.Direction.DESC, "createdAt", "id"),
        pagination,
        "cms-users",
        entity -> UserViewMapper.from(entity.toDomain()));
  }
}
