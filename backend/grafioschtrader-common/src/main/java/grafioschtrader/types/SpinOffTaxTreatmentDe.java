package grafioschtrader.types;

/** German tax treatment recorded for a spin-off. */
public enum SpinOffTaxTreatmentDe {
  UNDETERMINED((byte) 0), COST_SPLIT((byte) 1), TAXABLE_DISTRIBUTION((byte) 2);

  private final byte value;

  SpinOffTaxTreatmentDe(byte value) {
    this.value = value;
  }

  public byte getValue() {
    return value;
  }

  public static SpinOffTaxTreatmentDe getByValue(byte value) {
    for (SpinOffTaxTreatmentDe entry : values()) {
      if (entry.value == value) {
        return entry;
      }
    }
    throw new IllegalArgumentException("Unknown SpinOffTaxTreatmentDe: " + value);
  }
}
