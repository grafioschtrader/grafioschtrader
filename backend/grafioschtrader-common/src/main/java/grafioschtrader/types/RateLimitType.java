package grafioschtrader.types;

/**
 * Defines rate limiting strategies for generic feed connectors.
 * NONE disables rate limiting.
 * TOKEN_BUCKET uses a Bucket4j token bucket with configurable requests per period.
 * SEMAPHORE limits concurrent requests via a semaphore.
 */
public enum RateLimitType {
  NONE((byte) 0),
  TOKEN_BUCKET((byte) 1),
  SEMAPHORE((byte) 2);

  private final Byte value;

  private RateLimitType(final Byte value) {
    this.value = value;
  }

  public Byte getValue() {
    return this.value;
  }

  public static RateLimitType getByValue(byte value) {
    for (RateLimitType type : RateLimitType.values()) {
      if (type.getValue() == value) {
        return type;
      }
    }
    return null;
  }
}
