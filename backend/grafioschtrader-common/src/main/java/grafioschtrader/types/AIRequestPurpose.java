package grafioschtrader.types;

/**
 * What a round trip to a language model was made for. These values are saved in the database and must not be changed.
 */
public enum AIRequestPurpose {

  /** The model writes a template rule from the existing values of a user-defined field. */
  PROPOSE_RULE((byte) 1),

  /** The model proposes a value of a user-defined field for one security. */
  SUGGEST_VALUE((byte) 2),

  /** One request testing a tenant's saved key, without fallback. */
  TEST_KEY((byte) 3);

  private final Byte value;

  private AIRequestPurpose(final Byte value) {
    this.value = value;
  }

  public Byte getValue() {
    return this.value;
  }

  public static AIRequestPurpose getByValue(byte value) {
    for (AIRequestPurpose purpose : AIRequestPurpose.values()) {
      if (purpose.getValue() == value) {
        return purpose;
      }
    }
    return null;
  }
}
