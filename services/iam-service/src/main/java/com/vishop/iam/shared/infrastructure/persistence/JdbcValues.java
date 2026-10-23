package com.vishop.iam.shared.infrastructure.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class JdbcValues {
  private JdbcValues() {}

  public static UUID uuid(ResultSet r, String key) throws SQLException {
    return r.getObject(key, UUID.class);
  }

  public static <T> Optional<T> first(List<T> rows) {
    return rows.stream().findFirst();
  }
}
