package com.vishop.iam.modules.tenant.infrastructure.persistence.pagination;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vishop.iam.modules.tenant.application.dto.TenantListCriteria;
import com.vishop.iam.modules.tenant.domain.valueobject.TenantStatus;
import com.vishop.iam.modules.tenant.infrastructure.persistence.entities.TenantJpaEntity;
import io.github.coreplatform.common.domain.exception.BadRequestException;
import java.io.IOException;
import java.time.DateTimeException;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.UUID;
import org.springframework.data.domain.KeysetScrollPosition;
import org.springframework.data.domain.ScrollPosition;
import org.springframework.stereotype.Component;

/** Tenant cursors retain exact sort values, including sub-millisecond timestamps. */
@Component
public class TenantCursorCodec {
  private final ObjectMapper json;

  public TenantCursorCodec(ObjectMapper json) {
    this.json = json;
  }

  public KeysetScrollPosition decode(String token, UUID userId, TenantListCriteria criteria) {
    if (token == null || token.isBlank()) return ScrollPosition.keyset();
    try {
      if (token.length() > 4096) throw new IllegalArgumentException();
      var state = json.readValue(Base64.getUrlDecoder().decode(token), State.class);
      if (state == null
          || state.version() != 1
          || !userId.equals(state.userId())
          || !criteria.equals(state.criteria())
          || state.id() == null
          || state.value() == null) {
        throw new IllegalArgumentException();
      }
      Object value =
          switch (criteria.sortBy()) {
            case "id" -> UUID.fromString(state.value());
            case "createdAt" -> Instant.parse(state.value());
            case "status" -> TenantStatus.valueOf(state.value());
            default -> state.value();
          };
      if ("id".equals(criteria.sortBy()) && !state.id().equals(value))
        throw new IllegalArgumentException();
      var keys = new LinkedHashMap<String, Object>();
      keys.put(criteria.sortBy(), value);
      keys.put("id", state.id());
      return ScrollPosition.forward(keys);
    } catch (IOException | IllegalArgumentException | DateTimeException failure) {
      throw new BadRequestException(
          "Invalid tenant cursor or cursor does not match user, filters and sort");
    }
  }

  public String encode(UUID userId, TenantListCriteria criteria, TenantJpaEntity last) {
    String value =
        switch (criteria.sortBy()) {
          case "id" -> last.getId().toString();
          case "code" -> last.getCode();
          case "name" -> last.getName();
          case "status" -> last.getStatus().name();
          default -> last.getCreatedAt().toString();
        };
    try {
      return Base64.getUrlEncoder()
          .withoutPadding()
          .encodeToString(
              json.writeValueAsBytes(new State(1, userId, criteria, value, last.getId())));
    } catch (JsonProcessingException failure) {
      throw new IllegalStateException("Cannot encode tenant cursor", failure);
    }
  }

  private record State(
      int version, UUID userId, TenantListCriteria criteria, String value, UUID id) {}
}
