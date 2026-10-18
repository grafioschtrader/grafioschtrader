package grafioschtrader.types;

/**
 * Where the value of a user-defined security field comes from. A field other than {@link #MANUAL} computes its value
 * instead of the user typing it; the provenance of every computed cell is recorded in {@code udf_data_fill}. These
 * values are saved in the database and must not be changed.
 */
public enum UDFFillSource {

  /** The user types the value; nothing automatic touches the field. The default of every field. */
  MANUAL((byte) 0),

  /** A rule with placeholders from the master data of the security, evaluated by the instance itself. */
  TEMPLATE((byte) 1),

  /** A built-in lookup against an external, keyed source. */
  RESOLVER((byte) 2),

  /** A language model proposes values, which the user accepts. */
  LLM((byte) 3);

  private final Byte value;

  private UDFFillSource(final Byte value) {
    this.value = value;
  }

  public Byte getValue() {
    return this.value;
  }

  public static UDFFillSource getByValue(byte value) {
    for (UDFFillSource fillSource : UDFFillSource.values()) {
      if (fillSource.getValue() == value) {
        return fillSource;
      }
    }
    return null;
  }
}
