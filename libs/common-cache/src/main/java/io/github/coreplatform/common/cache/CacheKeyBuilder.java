package io.github.coreplatform.common.cache;

import java.util.Objects;
import java.util.StringJoiner;

/** Ghép key cho mọi loại cache, không biết user, role hay nghiệp vụ của service. */
public final class CacheKeyBuilder {
  private final String prefix;

  /** Prefix được giữ nguyên; dùng dấu ':' cuối prefix khi muốn phân tách namespace. */
  public CacheKeyBuilder(String prefix) {
    this.prefix = Objects.requireNonNull(prefix, "prefix");
  }

  /** Ví dụ build("subscription", "plan", id) → prefix + subscription:plan:id. */
  public String build(String namespace, Object... parts) {
    var key = new StringJoiner(":").add(segment(namespace));
    Objects.requireNonNull(parts, "parts");
    for (Object part : parts) key.add(segment(part));
    return withPrefix(key.toString());
  }

  /** Thêm prefix đúng một lần tại lớp truy cập Redis; đầu vào là key logic chưa có prefix. */
  public String withPrefix(String logicalKey) {
    if (logicalKey == null || logicalKey.isBlank())
      throw new IllegalArgumentException("key must not be blank");
    return prefix + logicalKey;
  }

  private static String segment(Object value) {
    if (value == null) throw new IllegalArgumentException("Cache key segment must not be null");
    String text = value.toString();
    if (text.isBlank() || !text.equals(text.trim()) || text.contains(":"))
      throw new IllegalArgumentException(
          "Cache key segment must be nonblank and must not contain ':'");
    return text;
  }
}
