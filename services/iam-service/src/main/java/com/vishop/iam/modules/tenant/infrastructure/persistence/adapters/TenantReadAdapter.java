package com.vishop.iam.modules.tenant.infrastructure.persistence.adapters;

import com.vishop.iam.modules.tenant.application.dto.TenantListCriteria;
import com.vishop.iam.modules.tenant.application.dto.TenantResponseDto;
import com.vishop.iam.modules.tenant.application.mapper.TenantResponseMapper;
import com.vishop.iam.modules.tenant.application.port.TenantReadPort;
import com.vishop.iam.modules.tenant.domain.valueobject.TenantStatus;
import com.vishop.iam.modules.tenant.infrastructure.persistence.entities.MembershipJpaEntity;
import com.vishop.iam.modules.tenant.infrastructure.persistence.entities.TenantJpaEntity;
import com.vishop.iam.modules.tenant.infrastructure.persistence.pagination.TenantCursorCodec;
import com.vishop.iam.modules.tenant.infrastructure.persistence.repositories.SpringDataTenantRepository;
import io.github.coreplatform.common.domain.exception.BadRequestException;
import io.github.coreplatform.common.domain.pagination.CursorParams;
import io.github.coreplatform.common.domain.pagination.OffsetPagination;
import io.github.coreplatform.common.domain.pagination.PagedResult;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;

@Repository
public class TenantReadAdapter implements TenantReadPort {
  private final SpringDataTenantRepository tenants;
  private final TenantCursorCodec cursors;

  public TenantReadAdapter(SpringDataTenantRepository tenants, TenantCursorCodec cursors) {
    this.tenants = tenants;
    this.cursors = cursors;
  }

  @Override
  public PagedResult<TenantResponseDto> findByUserId(
      UUID userId, OffsetPagination pagination, TenantListCriteria criteria) {
    var filter = filter(userId, criteria);

    // JPA's first-result offset is an int. Still return totals for out-of-range
    // pages.
    if (pagination.offset() > Integer.MAX_VALUE) {
      long total = tenants.count(filter);
      if (pagination.offset() < total) {
        throw new BadRequestException("Requested pagination offset is too large");
      }
      return PagedResult.ofOffset(List.of(), pagination.page(), pagination.limit(), total);
    }
    Sort sort = sort(criteria);
    var page =
        tenants.findAll(filter, PageRequest.of(pagination.pageIndex(), pagination.limit(), sort));
    var items = page.stream().map(entity -> TenantResponseMapper.from(entity.toDomain())).toList();
    return PagedResult.ofOffset(
        items, pagination.page(), pagination.limit(), page.getTotalElements());
  }

  @Override
  public PagedResult<TenantResponseDto> findByUserIdAfter(
      UUID userId, CursorParams pagination, TenantListCriteria criteria) {
    var position = cursors.decode(pagination.cursor(), userId, criteria);
    var window =
        tenants.findBy(
            filter(userId, criteria),
            query -> query.sortBy(sort(criteria)).limit(pagination.limit()).scroll(position));
    var entities = window.getContent();
    var items =
        entities.stream().map(entity -> TenantResponseMapper.from(entity.toDomain())).toList();
    String next = window.hasNext() ? cursors.encode(userId, criteria, entities.getLast()) : null;
    return PagedResult.ofCursor(items, window.hasNext(), next);
  }

  private static Sort sort(TenantListCriteria criteria) {
    Sort sort = Sort.by(Sort.Direction.fromString(criteria.sortDirection()), criteria.sortBy());
    return "id".equals(criteria.sortBy()) ? sort : sort.and(Sort.by("id"));
  }

  private static Specification<TenantJpaEntity> filter(UUID userId, TenantListCriteria criteria) {
    return (root, query, cb) -> {
      var predicates = new ArrayList<Predicate>();
      var membership = query.subquery(Integer.class);
      var member = membership.from(MembershipJpaEntity.class);
      membership
          .select(cb.literal(1))
          .where(
              cb.equal(member.get("tenantId"), root.get("id")),
              cb.equal(member.get("userId"), userId),
              cb.equal(member.get("status"), "ACTIVE"));
      predicates.add(cb.exists(membership));
      predicates.add(cb.equal(root.get("status"), TenantStatus.ACTIVE));
      if (criteria.id() != null) predicates.add(cb.equal(root.get("id"), criteria.id()));
      if (criteria.code() != null)
        predicates.add(cb.like(cb.lower(root.get("code")), contains(criteria.code()), '!'));
      if (criteria.name() != null)
        predicates.add(cb.like(cb.lower(root.get("name")), contains(criteria.name()), '!'));
      if (criteria.status() != null)
        predicates.add(
            cb.equal(root.get("status"), TenantStatus.valueOf(criteria.status().name())));
      if (criteria.createdFrom() != null)
        predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), criteria.createdFrom()));
      if (criteria.createdTo() != null)
        predicates.add(cb.lessThan(root.get("createdAt"), criteria.createdTo()));
      return cb.and(predicates.toArray(Predicate[]::new));
    };
  }

  private static String contains(String value) {
    String normalized = value.toLowerCase(Locale.ROOT);
    return "%" + normalized.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
  }
}
