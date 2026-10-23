package com.vishop.tenant.infrastructure.persistence.repositories;

import com.vishop.tenant.domain.valueobjects.TenantStatus;
import com.vishop.tenant.infrastructure.persistence.entities.TenantJpaEntity;
import jakarta.persistence.criteria.Predicate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

/**
 * JPA Specifications for dynamic Tenant queries (Filtering, Search, and Cursor
 * pagination).
 * Encapsulates dynamic query predicates using pure JPA Criteria API.
 */
public final class TenantSpecifications {

    private TenantSpecifications() {
    }

    public static Specification<TenantJpaEntity> withFilter(
            TenantStatus status,
            String search,
            Instant cursorCreatedAt,
            UUID cursorId) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }

            if (StringUtils.hasText(search)) {
                String pattern = "%" + search.toLowerCase().trim() + "%";
                Predicate nameLike = cb.like(cb.lower(root.get("name")), pattern);
                Predicate codeLike = cb.like(cb.lower(root.get("code")), pattern);
                predicates.add(cb.or(nameLike, codeLike));
            }

            if (cursorCreatedAt != null && cursorId != null) {
                Predicate beforeCreatedAt = cb.lessThan(root.get("createdAt"), cursorCreatedAt);
                Predicate sameCreatedAt = cb.equal(root.get("createdAt"), cursorCreatedAt);
                Predicate beforeId = cb.lessThan(root.get("id"), cursorId);
                predicates.add(cb.or(beforeCreatedAt, cb.and(sameCreatedAt, beforeId)));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
