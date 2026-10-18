package grafioschtrader.types;

/** Who pays for a language-model request. Persisted values must not change. */
public enum AIKeySource {
  TENANT((byte) 1), INSTANCE((byte) 2);

  private final byte value;

  AIKeySource(byte value) {
    this.value = value;
  }

  public byte getValue() {
    return value;
  }

  public static AIKeySource getByValue(byte value) {
    for (AIKeySource source : values()) {
      if (source.value == value) {
        return source;
      }
    }
    throw new IllegalArgumentException("Unknown AI key source: " + value);
  }
}
