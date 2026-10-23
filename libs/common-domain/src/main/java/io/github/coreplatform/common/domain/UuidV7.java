package io.github.coreplatform.common.domain;

import java.util.UUID;

/**
 * Generates UUID v7 values (RFC 9562).
 *
 * <p>UUID v7 is time-ordered, which keeps index locality and makes the cursor pagination contract
 * (createdAt + id) stable.
 *
 * <p>This is the only sanctioned way to generate identifiers for new entities. {@code
 * UUID.randomUUID()} (v4) must not be used because its random layout breaks cursor ordering and
 * index locality.
 */
public final class UuidV7 {

  // #region UuidV7
  private UuidV7() {}

  // #endregion

  // #region nextId
  /**
   * Creates a new time-ordered UUID v7 using the system clock.
   *
   * @return a fresh UUID v7
   */
  public static UUID nextId() {
    return fromTimestamp(System.currentTimeMillis());
  }

  // #endregion

  // #region fromTimestamp
  /**
   * Creates a UUID v7 for an explicit epoch-millisecond timestamp.
   *
   * @param epochMillis milliseconds since the Unix epoch
   * @return a UUID v7 carrying the given timestamp
   */
  public static UUID fromTimestamp(long epochMillis) {
    long timestamp = epochMillis & 0x0000_FFFF_FFFF_FFFFL;

    long mostSigBits = (timestamp & 0xFFFF_FFFF_FFFFL) << 16;
    // version 7 in the high nibble of octet 6
    mostSigBits |= 0x7000L;
    // 12 bits of millisecond sub-millisecond precision (rand_a)
    mostSigBits |= (randomBits() & 0x0FFFL);

    long leastSigBits = (randomBits() & 0x3FFFFFFFFFFFFFFFL);
    // variant 0b10 in octet 8
    leastSigBits |= 0x8000000000000000L;

    return new UUID(mostSigBits, leastSigBits);
  }

  // #endregion

  // #region randomBits
  private static long randomBits() {
    return SecureRandomHolder.NEXT.nextLong();
  }

  // #endregion

  // #region timestampOf
  /**
   * Extracts the embedded millisecond timestamp from a UUID v7.
   *
   * @param uuid the identifier to inspect
   * @return epoch milliseconds encoded in the identifier
   * @throws IllegalArgumentException if the identifier is not version 7
   */
  public static long timestampOf(UUID uuid) {
    if (uuid.version() != 7) {
      throw new IllegalArgumentException("Not a UUID v7: " + uuid);
    }
    return uuid.getMostSignificantBits() >>> 16;
  }

  // #endregion

  /** Holder so the shared generator is created once per classloader. */
  private static final class SecureRandomHolder {
    private static final java.util.random.RandomGenerator NEXT =
        java.util.random.RandomGenerator.getDefault();

    // #region SecureRandomHolder
    private SecureRandomHolder() {}
    // #endregion
  }
}
