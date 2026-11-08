package io.github.coreplatform.common.web.pagination;

import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Locale;

public enum PageMode {
  OFFSET,
  CURSOR;

  @JsonValue
  public String value() {
    return name().toLowerCase(Locale.ROOT);
  }
}
