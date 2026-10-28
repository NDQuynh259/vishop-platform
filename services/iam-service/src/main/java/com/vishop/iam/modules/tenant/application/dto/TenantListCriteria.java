package com.vishop.iam.modules.tenant.application.dto;

import com.vishop.iam.modules.tenant.domain.valueobject.TenantStatus;
import io.github.coreplatform.common.domain.exception.BadRequestException;
import java.time.Instant;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

public record TenantListCriteria(
    UUID id,
    String code,
    String name,
    TenantStatus status,
    Instant createdFrom,
    Instant createdTo,
    String sortBy,
    String sortDirection) {
  private static final Set<String> SORT_FIELDS =
      Set.of("id", "code", "name", "status", "createdAt");

  public TenantListCriteria {
    code = normalize(code, 50, "code");
    name = normalize(name, 200, "name");
    sortBy = sortBy == null || sortBy.isBlank() ? "name" : sortBy.trim();
    sortDirection =
        sortDirection == null || sortDirection.isBlank()
            ? "ASC"
            : sortDirection.trim().toUpperCase(Locale.ROOT);
    if (!SORT_FIELDS.contains(sortBy)) {
      throw new BadRequestException("Sort field must be id, code, name, status or createdAt");
    }
    if (!Set.of("ASC", "DESC").contains(sortDirection)) {
      throw new BadRequestException("Sort direction must be asc or desc");
    }
    if (createdFrom != null && createdTo != null && !createdFrom.isBefore(createdTo)) {
      throw new BadRequestException("createdFrom must be before createdTo");
    }
  }

  public static TenantListCriteria fromSort(
      UUID id,
      String code,
      String name,
      TenantStatus status,
      Instant createdFrom,
      Instant createdTo,
      String sort) {
    String value = sort == null || sort.isBlank() ? "createdAt:asc" : sort.trim();
    String[] parts = value.split(":", -1);
    if (parts.length != 2 || parts[0].isBlank() || parts[1].isBlank()) {
      throw new BadRequestException("sort must use field:direction, for example createdAt:desc");
    }
    return new TenantListCriteria(
        id, code, name, status, createdFrom, createdTo, parts[0], parts[1]);
  }

  private static String normalize(String value, int maxLength, String field) {
    if (value == null || value.isBlank()) return null;
    String normalized = value.trim();
    if (normalized.length() > maxLength) {
      throw new BadRequestException(field + " filter is too long");
    }
    return normalized;
  }
}
