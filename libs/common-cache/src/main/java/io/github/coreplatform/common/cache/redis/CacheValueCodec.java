package io.github.coreplatform.common.cache.redis;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.coreplatform.common.cache.CacheSerializationException;
import java.util.Objects;

/** Strings retain Redis's native format; structured values use application-configured JSON. */
final class CacheValueCodec {
  private final ObjectMapper json;

  CacheValueCodec(ObjectMapper json) {
    this.json = Objects.requireNonNull(json, "json");
  }

  String encode(Object value) {
    Objects.requireNonNull(value, "value");
    if (value instanceof String text) return text;
    try {
      return json.writeValueAsString(value);
    } catch (JsonProcessingException failure) {
      throw new CacheSerializationException("Cannot serialize cached value", failure);
    }
  }

  <T> T decode(String value, Class<T> type) {
    if (type == String.class) return type.cast(value);
    try {
      T decoded = json.readValue(value, type);
      if (decoded == null)
        throw new CacheSerializationException("Cached JSON must not be null", null);
      return decoded;
    } catch (JsonProcessingException failure) {
      throw new CacheSerializationException("Cannot deserialize cached value", failure);
    }
  }
}
