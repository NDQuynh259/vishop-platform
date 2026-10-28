package com.vishop.iam.modules.tenant.infrastructure.persistence.adapters;

import com.vishop.iam.modules.tenant.application.dto.TenantListCriteria;
import com.vishop.iam.modules.tenant.application.dto.TenantResponseDto;
import com.vishop.iam.modules.tenant.application.mapper.TenantResponseMapper;
import com.vishop.iam.modules.tenant.application.port.TenantReadPort;
import com.vishop.iam.modules.tenant.domain.valueobject.TenantStatus;
import com.vishop.iam.modules.tenant.infrastructure.persistence.entities.TenantJpaEntity;
import com.vishop.iam.modules.tenant.infrastructure.persistence.repositories.SpringDataTenantRepository;
import io.github.coreplatform.common.domain.exception.BadRequestException;
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

  public TenantReadAdapter(SpringDataTenantRepository tenants) {
    this.tenants = tenants;
  }

  @Override
  public PagedResult<TenantResponseDto> findByTenantIds(
      List<UUID> tenantIds, OffsetPagination pagination, TenantListCriteria criteria) {
    if (tenantIds.isEmpty()) {
      return PagedResult.ofOffset(List.of(), pagination.page(), pagination.limit(), 0);
    }
    Specification<TenantJpaEntity> filter =
        (root, query, cb) -> {
          var predicates = new ArrayList<Predicate>();
          predicates.add(root.get("id").in(tenantIds));
          predicates.add(cb.equal(root.get("status"), TenantStatus.ACTIVE));
          if (criteria.id() != null) predicates.add(cb.equal(root.get("id"), criteria.id()));
          if (criteria.code() != null)
            predicates.add(cb.like(cb.lower(root.get("code")), contains(criteria.code()), '!'));
          if (criteria.name() != null)
            predicates.add(cb.like(cb.lower(root.get("name")), contains(criteria.name()), '!'));
          if (criteria.status() != null)
            predicates.add(cb.equal(root.get("status"), criteria.status()));
          if (criteria.createdFrom() != null)
            predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), criteria.createdFrom()));
          if (criteria.createdTo() != null)
            predicates.add(cb.lessThan(root.get("createdAt"), criteria.createdTo()));
          return cb.and(predicates.toArray(Predicate[]::new));
        };

    // JPA's first-result offset is an int. Still return totals for out-of-range pages.
    if (pagination.offset() > Integer.MAX_VALUE) {
      long total = tenants.count(filter);
      if (pagination.offset() < total) {
        throw new BadRequestException("Requested pagination offset is too large");
      }
      return PagedResult.ofOffset(List.of(), pagination.page(), pagination.limit(), total);
    }
    Sort sort = Sort.by(Sort.Direction.fromString(criteria.sortDirection()), criteria.sortBy());
    if (!"id".equals(criteria.sortBy())) sort = sort.and(Sort.by("id"));
    var page =
        tenants.findAll(filter, PageRequest.of(pagination.pageIndex(), pagination.limit(), sort));
    var items = page.stream().map(entity -> TenantResponseMapper.from(entity.toDomain())).toList();
    return PagedResult.ofOffset(
        items, pagination.page(), pagination.limit(), page.getTotalElements());
  }

  private static String contains(String value) {
    String normalized = value.toLowerCase(Locale.ROOT);
    return "%" + normalized.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
  }
}
