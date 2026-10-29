package com.vishop.subscription.database;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import java.util.List;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Independent SQL assertions and fixtures through JPA, participating in an existing transaction.
 */
public final class JpaDatabaseFixture {
  private final EntityManager entityManager;
  private final TransactionTemplate transactions;

  public JpaDatabaseFixture(EntityManager entityManager, PlatformTransactionManager manager) {
    this.entityManager = entityManager;
    this.transactions = new TransactionTemplate(manager);
  }

  public void execute(String sql) {
    update(sql);
  }

  public int update(String sql, Object... parameters) {
    return transactions.execute(
        status -> {
          entityManager.flush();
          int changed = query(sql, parameters).executeUpdate();
          entityManager.clear();
          return changed;
        });
  }

  public <T> T queryForObject(String sql, Class<T> type, Object... parameters) {
    return transactions.execute(status -> convert(query(sql, parameters).getSingleResult(), type));
  }

  public <T> List<T> queryForList(String sql, Class<T> type, Object... parameters) {
    return transactions.execute(
        status -> {
          List<?> values = query(sql, parameters).getResultList();
          return values.stream().map(value -> convert(value, type)).toList();
        });
  }

  private Query query(String sql, Object[] parameters) {
    var query = entityManager.createNativeQuery(sql);
    for (int i = 0; i < parameters.length; i++) query.setParameter(i + 1, parameters[i]);
    return query;
  }

  private <T> T convert(Object value, Class<T> type) {
    if (value instanceof Number number && type == Integer.class)
      return type.cast(number.intValue());
    return type.cast(value);
  }
}
