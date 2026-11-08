package io.github.coreplatform.common.domain.pagination;

import io.github.coreplatform.common.domain.exception.BadRequestException;
import java.io.*;
import java.util.*;

/** Versioned opaque keyset cursor, bound to a list and its filters/sort. */
public final class ListCursor {
  private ListCursor() {}

  public static String encode(String scope, List<String> keys) {
    try {
      var bytes = new ByteArrayOutputStream();
      try (var out = new DataOutputStream(bytes)) {
        out.writeInt(1);
        out.writeUTF(scope);
        out.writeInt(keys.size());
        for (String key : keys) out.writeUTF(key);
      }
      return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes.toByteArray());
    } catch (IOException failure) {
      throw new IllegalStateException("Cannot encode list cursor", failure);
    }
  }

  public static List<String> decode(String token, String scope, int keyCount) {
    if (token == null || token.isBlank()) return List.of();
    try {
      if (token.length() > 16384) throw new IllegalArgumentException();
      try (var input =
          new DataInputStream(new ByteArrayInputStream(Base64.getUrlDecoder().decode(token)))) {
        if (input.readInt() != 1 || !scope.equals(input.readUTF()) || input.readInt() != keyCount)
          throw new IllegalArgumentException();
        var keys = new ArrayList<String>();
        for (int i = 0; i < keyCount; i++) keys.add(input.readUTF());
        if (input.available() != 0) throw new IllegalArgumentException();
        return List.copyOf(keys);
      }
    } catch (IOException | IllegalArgumentException failure) {
      throw new BadRequestException(
          "Invalid cursor or cursor does not match this list and its filters");
    }
  }
}
