package io.github.coreplatform.common.persistence.pagination;

import io.github.coreplatform.common.domain.exception.BadRequestException;
import io.github.coreplatform.common.domain.pagination.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.function.Function;
import org.springframework.beans.PropertyAccessorFactory;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

/** Database pagination with a shared scope/filter and a deterministic, non-null sort key. */
public final class JpaListPagination {
  private JpaListPagination() {}

  @SuppressWarnings({"unchecked", "rawtypes"})
  public static <E, T> PagedResult<T> find(
      EntityManager em,
      Class<E> type,
      Specification<E> filter,
      Sort sort,
      ListPagination pagination,
      String scope,
      Function<E, T> mapper) {
    if (sort.stream().noneMatch(order -> order.getProperty().equals("id")))
      sort = sort.and(Sort.by("id"));
    var orders = sort.toList();
    String cursorScope = scope + "|" + sort;
    var cb = em.getCriteriaBuilder();
    var query = cb.createQuery(type);
    var root = query.from(type);
    var conditions = new ArrayList<Predicate>();
    var base = filter.toPredicate(root, query, cb);
    if (base != null) conditions.add(base);
    if (pagination.isCursor()) {
      var keys = ListCursor.decode(pagination.cursor().cursor(), cursorScope, orders.size());
      if (!keys.isEmpty()) {
        var alternatives = new ArrayList<Predicate>();
        var prefix = new ArrayList<Predicate>();
        try {
          for (int i = 0; i < orders.size(); i++) {
            var order = orders.get(i);
            Path path = root.get(order.getProperty());
            Comparable value = value(keys.get(i), path.getJavaType());
            var branch = new ArrayList<>(prefix);
            branch.add(
                order.isAscending() ? cb.greaterThan(path, value) : cb.lessThan(path, value));
            alternatives.add(cb.and(branch.toArray(Predicate[]::new)));
            prefix.add(cb.equal(path, value));
          }
        } catch (RuntimeException failure) {
          throw new BadRequestException("Invalid list cursor sort values");
        }
        conditions.add(cb.or(alternatives.toArray(Predicate[]::new)));
      }
    }
    query.select(root).where(conditions.toArray(Predicate[]::new));
    query.orderBy(
        orders.stream()
            .map(
                order ->
                    order.isAscending()
                        ? cb.asc(root.get(order.getProperty()))
                        : cb.desc(root.get(order.getProperty())))
            .toList());
    var rowsQuery = em.createQuery(query);
    if (!pagination.isCursor()) {
      var count = cb.createQuery(Long.class);
      var countRoot = count.from(type);
      count.select(cb.count(countRoot));
      var predicate = filter.toPredicate(countRoot, count, cb);
      if (predicate != null) count.where(predicate);
      long total = em.createQuery(count).getSingleResult();
      var offset = pagination.offset();
      if (offset.offset() >= total)
        return PagedResult.ofOffset(List.of(), offset.page(), offset.limit(), total);
      if (offset.offset() > Integer.MAX_VALUE)
        throw new BadRequestException("Requested pagination offset is too large");
      var rows =
          rowsQuery
              .setFirstResult((int) offset.offset())
              .setMaxResults(offset.limit())
              .getResultList();
      return PagedResult.ofOffset(
          rows.stream().map(mapper).toList(), offset.page(), offset.limit(), total);
    }
    var rows = rowsQuery.setMaxResults(pagination.limit() + 1).getResultList();
    boolean hasNext = rows.size() > pagination.limit();
    var page = rows.stream().limit(pagination.limit()).toList();
    String next = null;
    if (hasNext) {
      var accessor = PropertyAccessorFactory.forBeanPropertyAccess(page.getLast());
      next =
          ListCursor.encode(
              cursorScope,
              orders.stream()
                  .map(
                      order ->
                          Objects.requireNonNull(
                                  accessor.getPropertyValue(order.getProperty()),
                                  "Cursor sort keys must not be null")
                              .toString())
                  .toList());
    }
    return PagedResult.ofCursor(page.stream().map(mapper).toList(), hasNext, next);
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private static Comparable value(String value, Class<?> type) {
    if (type == String.class) return value;
    if (type == UUID.class) return UUID.fromString(value);
    if (type == Instant.class) return Instant.parse(value);
    if (type == Long.class || type == long.class) return Long.valueOf(value);
    if (type == Integer.class || type == int.class) return Integer.valueOf(value);
    if (type == BigDecimal.class) return new BigDecimal(value);
    if (type.isEnum()) return Enum.valueOf((Class) type, value);
    throw new IllegalArgumentException("Unsupported cursor sort type: " + type);
  }
}
